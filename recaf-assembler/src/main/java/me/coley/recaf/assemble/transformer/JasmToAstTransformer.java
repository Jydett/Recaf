package me.coley.recaf.assemble.transformer;

import me.coley.recaf.assemble.ast.*;
import me.coley.recaf.assemble.ast.arch.*;
import me.coley.recaf.assemble.ast.insn.*;
import me.coley.recaf.assemble.ast.meta.Expression;
import me.coley.recaf.assemble.ast.meta.Label;
import me.coley.recaf.assemble.ast.meta.Signature;
import me.coley.recaf.util.EscapeUtil;
import me.darknet.assembler.compiler.FieldDescriptor;
import me.darknet.assembler.compiler.MethodDescriptor;
import me.darknet.assembler.parser.AssemblerException;
import me.darknet.assembler.parser.Group;
import me.darknet.assembler.parser.MethodParameter;
import me.darknet.assembler.parser.Token;
import me.darknet.assembler.parser.groups.*;
import me.darknet.assembler.transform.MethodVisitor;
import me.darknet.assembler.transform.Transformer;
import me.darknet.assembler.transform.Visitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.*;

/**
 * JASM visitor to generate AST instances.
 *
 * @author Nowilltolife
 */
public class JasmToAstTransformer implements Visitor, MethodVisitor {

    Collection<Group> groups;
    Unit unit;
    AbstractDefinition activeMember;
    Code code = new Code();
    Attributes currentAttributes = new Attributes();

    public JasmToAstTransformer(Collection<Group> groups) {
        this.groups = groups;
    }

    public Unit generateUnit() throws AssemblerException {
        Transformer transformer = new Transformer(this);
        transformer.transform(groups);
        return unit;
    }

    public String content(Group group) {
        return EscapeUtil.unescapeUnicode(group.content());
    }

    public void add(CodeEntry element) {
        code.add(wrap(latestGroup, (BaseElement & CodeEntry) element));
    }

    Group latestGroup;

    @Override
    public void visit(Group group) throws AssemblerException {
        latestGroup = group;
    }

    @Override
    public void visitLabel(LabelGroup label) throws AssemblerException {
        code.addLabel(new Label(label.getLabel()));
    }

    @Override
    public void visitLookupSwitchInsn(LookupSwitchGroup lookupSwitch) throws AssemblerException {
        List<LookupSwitchInstruction.Entry> entries = new ArrayList<>();
        for (CaseLabelGroup caseLabel : lookupSwitch.caseLabels) {
            entries.add(new LookupSwitchInstruction.Entry(caseLabel.key.asInt(), caseLabel.value.getLabel()));
        }
        add(new LookupSwitchInstruction(Opcodes.LOOKUPSWITCH, entries, lookupSwitch.defaultLabel.getLabel()));
    }

    @Override
    public void visitTableSwitchInsn(TableSwitchGroup tableSwitch) throws AssemblerException {
        List<String> lables = new ArrayList<>();
        for (LabelGroup labelGroup : tableSwitch.getLabelGroups()) {
            lables.add(labelGroup.getLabel());
        }
        add(new TableSwitchInstruction(Opcodes.TABLESWITCH,
                tableSwitch.getMin().asInt(),
                tableSwitch.getMax().asInt(),
                lables,
                tableSwitch.getDefaultLabel().getLabel()));
    }

    @Override
    public void visitCatch(CatchGroup catchGroup) throws AssemblerException {
        code.add(new TryCatch(
                catchGroup.getBegin().getLabel(),
                catchGroup.getEnd().getLabel(),
                catchGroup.getHandler().getLabel(),
                catchGroup.getException().content()));
    }

    @Override
    public void visitVarInsn(int opcode, IdentifierGroup identifier) throws AssemblerException {
        add(new VarInstruction(opcode, content(identifier)));
    }

    @Override
    public void visitDirectVarInsn(int opcode, int var) throws AssemblerException {

    }

    @Override
    public void visitMethodInsn(int opcode, IdentifierGroup desc, boolean itf) throws AssemblerException {
        MethodDescriptor md = new MethodDescriptor(desc.content(), false);
        add(new MethodInstruction(opcode, md.owner, md.name, md.getDescriptor()));
    }

    @Override
    public void visitFieldInsn(int opcode, IdentifierGroup name, IdentifierGroup desc) throws AssemblerException {
        FieldDescriptor fs = new FieldDescriptor(name.content());
        add(new FieldInstruction(opcode, fs.owner, fs.name, desc.content()));
    }

    @Override
    public void visitJumpInsn(int opcode, LabelGroup label) throws AssemblerException {
        add(new JumpInstruction(opcode, label.getLabel()));
    }

    @Override
    public void visitLdcInsn(Group constant) throws AssemblerException {
        add(new LdcInstruction(Opcodes.LDC, convert(constant), from(constant)));
    }

    @Override
    public void visitTypeInsn(int opcode, IdentifierGroup type) throws AssemblerException {
        add(new TypeInstruction(opcode, content(type)));
    }

    @Override
    public void visitIincInsn(IdentifierGroup var, int value) throws AssemblerException {
        add(new IincInstruction(Opcodes.IINC, var.content(), value));
    }

    @Override
    public void visitIntInsn(int opcode, int value) throws AssemblerException {
        if(opcode == Opcodes.NEWARRAY) {
            add(new NewArrayInstruction(opcode, NewArrayInstruction.fromInt(value)));
        }else
            add(new IntInstruction(opcode, value));
    }

    @Override
    public void visitLineNumber(NumberGroup line, IdentifierGroup label) throws AssemblerException {
        add(new LineInstruction(-1, label.content(), line.asInt()));
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) throws AssemblerException {
        add(new MultiArrayInstruction(Opcodes.MULTIANEWARRAY, desc, dims));
    }

    public HandleInfo from(HandleGroup handle) throws AssemblerException {
        if(handle.isField()) {
            FieldDescriptor fs = new FieldDescriptor(content(handle.getDescriptor()));
            return new HandleInfo(handle.getHandleType().content(), fs.owner, fs.name, content(handle.getFieldDescriptor()));
        }else {
            MethodDescriptor mdh = new MethodDescriptor(handle.getDescriptor().content(), false);
            return new HandleInfo(
                    handle.getHandleType().content(),
                    mdh.owner,
                    mdh.name,
                    mdh.getDescriptor());
        }
    }

    @Override
    public void visitInvokeDyanmicInsn(String identifier, IdentifierGroup descriptor, HandleGroup handle, ArgsGroup args) throws AssemblerException {
        HandleInfo handleInfo = from(handle);
        List<IndyInstruction.BsmArg> bsmArgs = new ArrayList<>();
        for (Group arg : args.getBody().getChildren()) {
            bsmArgs.add(new IndyInstruction.BsmArg(from(arg), convert(arg)));
        }
        add(new IndyInstruction(
                Opcodes.INVOKEDYNAMIC,
                identifier,
                descriptor.content(),
                handleInfo,
                bsmArgs));
    }

    public ArgType from(Group group) {
        if(group instanceof NumberGroup) {
            NumberGroup number = (NumberGroup) group;
            if(number.isFloat()) {
                return number.isWide() ? ArgType.DOUBLE : ArgType.FLOAT;
            }else {
                return number.isWide() ? ArgType.LONG : ArgType.INTEGER;
            }
        }else if(group instanceof StringGroup) {
            return ArgType.STRING;
        }else if(group instanceof TypeGroup) {
            return ArgType.TYPE;
        }else if(group instanceof HandleGroup) {
            return ArgType.HANDLE;
        }
        throw new IllegalArgumentException("Cannot convert to constant '" + group.content() + "'");
    }

    @Override
    public void visitInsn(int opcode) throws AssemblerException {
        add(new Instruction(opcode));
    }

    @Override
    public void visitExpr(ExprGroup expr) throws AssemblerException {
        add(new Expression(expr.textGroup.content()));
    }

    @Override
    public void visitClass(AccessModsGroup accessMods, IdentifierGroup identifier) throws AssemblerException {

    }

    @Override
    public void visitSuper(ExtendsGroup extendsGroup) throws AssemblerException {

    }

    @Override
    public void visitImplements(ImplementsGroup implementsGroup) throws AssemblerException {

    }

    @Override
    public void visitField(AccessModsGroup accessMods, IdentifierGroup name, IdentifierGroup descriptor, Group constantValue) throws AssemblerException {

        FieldDefinition field = new FieldDefinition(fromAccessMods(accessMods), name.content(), descriptor.content());

        if(currentAttributes.getSignature() != null) {
            field.setSignature(currentAttributes.getSignature());
        }
        for(Annotation annotation : currentAttributes.getAnnotations()) {
            field.addAnnotation(annotation);
        }

        activeMember = field;
        if(constantValue != null)
            field.setConstVal(new ConstVal(convert(constantValue), from(constantValue)));

        currentAttributes.clear();
    }

    @Override
    public MethodVisitor visitMethod(AccessModsGroup accessMods, IdentifierGroup descriptor, BodyGroup body) throws AssemblerException {
        MethodDescriptor md = new MethodDescriptor(descriptor.content(), true);
        MethodParameters parameters = new MethodParameters();
        for(MethodParameter mp : md.parameters) {
            parameters.add(new me.coley.recaf.assemble.ast.arch.MethodParameter(mp.getDescriptor(), mp.getName()));
        }
        this.code = new Code();
        MethodDefinition method = new MethodDefinition(
                fromAccessMods(accessMods),
                md.name,
                parameters,
                md.returnType,
                this.code);
        for(ThrownException thrown : currentAttributes.getThrownExceptions()) {
            method.addThrownException(thrown);
        }
        if(currentAttributes.getSignature() != null) {
            method.setSignature(currentAttributes.getSignature());
        }
        for(Annotation annotation : currentAttributes.getAnnotations()) {
            method.addAnnotation(annotation);
        }
        currentAttributes.clear();
        activeMember = method;
        return this;
    }

    public Modifiers fromAccessMods(AccessModsGroup accessMods) {
        Modifiers modifiers = new Modifiers();
        for(AccessModGroup accessMod : accessMods.accessMods) {
            modifiers.add(Modifier.byName(accessMod.content().replace(".", "")));
        }
        return modifiers;
    }

    public void paramValue(String name, Group value, Map<String, Annotation.AnnoArg> map) throws AssemblerException{

        if(value.type == Group.GroupType.ARGS){
            ArgsGroup args = (ArgsGroup) value;
            for (Group group : args.getBody().children) {
                paramValue(name, group, map);
            }
        } else if(value.type == Group.GroupType.ENUM) {
            EnumGroup enumGroup = (EnumGroup) value;
            map.put(name,
                        new Annotation.AnnoEnum(
                            enumGroup.getDescriptor().content(),
                            enumGroup.getEnumValue().content()
                        ));
        } else if(value.type == Group.GroupType.ANNOTATION) {
            AnnotationGroup annotationGroup = (AnnotationGroup) value;
            Map<String, Annotation.AnnoArg> map2 = new HashMap<>();
            for(AnnotationParamGroup param : annotationGroup.getParams()) {
                annotationParam(param, map2);
            }
            map.put(name,
                    new Annotation.AnnoArg(
                            ArgType.ANNO,
                            new Annotation(
                                    !annotationGroup.isInvisible(),
                                    annotationGroup.getClassGroup().content(),
                                    map2
                            )));
        } else {
            map.put(name,
                    new Annotation.AnnoArg(
                            from(value),
                            convert(value)
                    ));
        }

    }

    public void annotationParam(AnnotationParamGroup annotationParam, Map<String, Annotation.AnnoArg> map) throws AssemblerException {
        if(annotationParam.value.type == Group.GroupType.ARGS) {
            ArgsGroup args = (ArgsGroup) annotationParam.value;
            Map<String, Annotation.AnnoArg> map2 = new HashMap<>();
            for (Group group : args.getBody().children) {
                paramValue(group.content(), group, map2);
            }
            map.put(annotationParam.name.content(),
                    new Annotation.AnnoArg(
                            ArgType.ANNO_LIST,
                            new ArrayList<>(map2.values())
                    ));
        }else {
            paramValue(annotationParam.name.content(), annotationParam.value, map);
        }
    }

    @Override
    public void visitAnnotation(AnnotationGroup annotation) throws AssemblerException {

        Map<String, Annotation.AnnoArg> args = new HashMap<>();
        for (AnnotationParamGroup param : annotation.getParams()) {
            annotationParam(param, args);
        }

        Annotation anno = new Annotation(annotation.isInvisible(), annotation.getClassGroup().content(), args);
        currentAttributes.addAnnotation(anno);
    }

    @Override
    public void visitSignature(SignatureGroup signature) throws AssemblerException {
        currentAttributes.setSignature(new Signature(signature.getDescriptor().content()));
    }

    @Override
    public void visitThrows(ThrowsGroup throwsGroup) throws AssemblerException {
        currentAttributes.addThrownException(new ThrownException(throwsGroup.getClassName().content()));
    }

    @Override
    public void visitExpression(ExprGroup expr) throws AssemblerException {

    }

    @Override
    public void visitEnd() throws AssemblerException {
        unit = new Unit(activeMember);
    }

    @Override
    public void visitEndClass() throws AssemblerException {
        if(activeMember != null && activeMember.isField())
            unit = new Unit(activeMember);
    }

    public Object convert(Group group) throws AssemblerException {
        if (group.getType() == Group.GroupType.NUMBER) {
            return ((NumberGroup) group).getNumber();
        } else if(group.type == Group.GroupType.TYPE) {
            TypeGroup typeGroup = (TypeGroup) group;
            try {
                String desc = typeGroup.descriptor.content();
                if(desc.isEmpty()) return Type.getType(desc);
                if(desc.charAt(0) == '(') {
                    return Type.getMethodType(typeGroup.descriptor.content());
                } else {
                    return Type.getObjectType(typeGroup.descriptor.content());
                }
            } catch (IllegalArgumentException e) {
                throw new AssemblerException("Invalid type: " + typeGroup.descriptor.content(), typeGroup.location());
            }
        } else if(group.type == Group.GroupType.HANDLE) {
            HandleGroup handle = (HandleGroup) group;
            HandleInfo info = from(handle);
            return info.toHandle();
        }else {
            return group.content();
        }
    }

    private static <E extends BaseElement> E wrap(Group group, E element) {
        Token start = group.value;
        Token end = group.end();
        if(end == null)
            end = start;
        return element.setLine(start.getLocation().getLine()).setRange(
                start.getLocation().getStartPosition(),
                end.getLocation().getEndPosition());
    }
}
