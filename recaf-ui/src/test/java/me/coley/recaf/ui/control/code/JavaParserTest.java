package me.coley.recaf.ui.control.code;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import me.coley.recaf.ui.control.code.LanguageStyler.Section;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JavaParserTest {

	private static final List<Section> styledSections = new ArrayList<>();
	private static LanguageStyler languageStyler;

	@BeforeAll
	static void setup() {
		styledSections.clear();
		languageStyler = new LanguageStyler(Languages.JAVA, new Styleable() {
			@Override
			public Collection<String> getStyleAtPosition(int pos) {
				return Collections.emptyList();
			}

			@Override
			public CompletableFuture<Void> onClearStyle() {
				styledSections.clear();
				return CompletableFuture.completedFuture(null);
			}

			@Override
			public CompletableFuture<Void> onApplyStyle(int start, List<Section> sections) {
				styledSections.addAll(sections.stream().filter(s -> s.classes.contains("constant")).collect(Collectors.toList()));
				return CompletableFuture.completedFuture(null);
			}
		});
	}

	@Test
	public void testJavaConstantStyling() {
		languageStyler.styleCompleteDocument("public class ConstantsShowcase {\n"
												 + "\n"
												 + "    // Various constant declarations\n"
												 + "    public static final boolean FLAG = true;\n"
												 + "    public static final boolean FLAG_2 = false;\n"
												 + "    public static final int DECIMAL_CONSTANT = 42;\n"
												 + "    public static final long LONG_CONSTANT = 123456789l;\n"
												 + "    public static final int OCTAL_CONSTANT = 0757;\n"
												 + "    public static final int HEX_CONSTANT = 0x1a2b3c;\n"
												 + "    public static final int BINARY_CONSTANT = 0b101011;\n"
												 + "    public static final double FLOATING_POINT_CONSTANT = 3.14159;\n"
												 + "    public static final double EXPONENTIAL_CONSTANT = 1.234e2;\n"
												 + "    public static final float FLOAT_CONSTANT = 2.71828f;\n"
												 + "    public static final String GREETING = \"Hello, World!\";\n"
												 + "    public static final char CHAR_CONSTANT = 'A';\n"
												 + "    public static final char ESCAPED_CHAR_CONSTANT = '\\n';\n"
												 + "\n"
												 + "    /**\n"
												 + "    * This is a great javadoc header\n"
												 + "    **/\n"
												 + "    public static void main(String[] args) {\n"
												 + "        float x = .6F;\n"
												 + "        double t = .8D;\n"
												 + "        long l = 0L;\n"
												 + "        while (false) {\n"
												 + "            if (true) args[-1] = \"'3'\";\n"
												 + "        }\n"
												 + "        // Using constants in calculations\n"
												 + "        int total = DECIMAL_CONSTANT + OCTAL_CONSTANT + HEX_CONSTANT + BINARY_CONSTANT;\n"
												 + "        double scientific = FLOATING_POINT_CONSTANT * EXPONENTIAL_CONSTANT;\n"
												 + "\n"
												 + "        // Using constants in output\n"
												 + "        System.out.println(GREETING);\n"
												 + "        System.out.println(\"FLAG is set to: \" + FLAG);\n"
												 + "        System.out.println(\"Total of integer constants: \" + total);\n"
												 + "        System.out.println(\"Scientific calculation result: \" + scientific);\n"
												 + "        System.out.println(\"Float constant value: \" + FLOAT_CONSTANT);\n"
												 + "        System.out.println(\"Character constants: \" + CHAR_CONSTANT + \" \" + ESCAPED_CHAR_CONSTANT);\n"
												 + "\n"
												 + "        // Control structures with constants\n"
												 + "        if (FLAG) {\n"
												 + "            System.out.println(\"The flag is true!\");\n"
												 + "        }\n"
												 + "\n"
												 + "        for (int i = 0; i < DECIMAL_CONSTANT; i++) {\n"
												 + "            System.out.println(\"Counting: \" + i);\n"
												 + "        }\n"
												 + "\n"
												 + "        // Edge cases and non-constants\n"
												 + "        String notAConstant = \"Variable string\";\n"
												 + "        int result = someMethod(HEX_CONSTANT, LONG_CONSTANT, null);\n"
												 + "        char[] charArray = {CHAR_CONSTANT, ESCAPED_CHAR_CONSTANT};\n"
												 + "\n"
												 + "    }\n"
												 + "\n"
												 + "    /* this is a wonderful javadoc comment */\n"
												 + "    private static int someMethod(int hex, long constant) {\n"
												 + "        // Imagine some complex logic here that uses the constants provided\n"
												 + "        return hex + (int)constant;\n"
												 + "    }\n"
												 + "}");

		Assertions.assertEquals(String.join("\n", new String[]{
			"true", "false", "42", "123456789l", "0757", "0x1a2b3c",
			"0b101011", "3.14159", "1.234e2", "2.71828f", "'A'", "'\\n'",
			".6F", ".8D", "0L", "false", "true", "1", "0", "null"}), styledSections.stream().map(s -> s.text).collect(Collectors.joining("\n")));
	}
}
