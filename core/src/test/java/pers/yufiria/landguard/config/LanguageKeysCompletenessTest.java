package pers.yufiria.landguard.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 13：语言文件完整性回归。
 * 历史缺陷：group/economy/buy/sell/bank/admin 六节曾误置于 yml 顶层，
 * 而 Languages 常量键为 command.xxx，导致运行时回退显示原始键。
 * 本测试按缩进解析 yml 全路径叶子键，与 Languages.java 声明逐一比对（zh_cn / en_us 各一份）。
 */
public class LanguageKeysCompletenessTest {

    // Gradle 测试工作目录为 core/；语言文件在 root 模块
    private static final Path LANG_DIR = Path.of("..", "src", "main", "resources", "lang");
    private static final Path LANGUAGES_JAVA =
        Path.of("src", "main", "java", "pers", "yufiria", "landguard", "config", "Languages.java");

    private static final Pattern KEY_PATTERN = Pattern.compile("StringLangEntry\\(\"([^\"]+)\"\\)");
    private static final Pattern LINE_PATTERN = Pattern.compile("^(\\s*)([A-Za-z0-9_]+):");

    @Test
    void everyDeclaredKeyExistsInBothLangFiles() throws IOException {
        Set<String> declared = declaredKeys();
        for (String lang : List.of("zh_cn", "en_us")) {
            Map<String, Boolean> yamlKeys = flatYamlKeys(LANG_DIR.resolve(lang + ".yml"));
            Set<String> missing = new LinkedHashSet<>();
            for (String key : declared) {
                if (!yamlKeys.containsKey(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(),
                lang + ".yml 缺少 " + missing.size() + " 个语言键: " + missing);
        }
    }

    private Set<String> declaredKeys() throws IOException {
        String source = Files.readString(LANGUAGES_JAVA, StandardCharsets.UTF_8);
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = KEY_PATTERN.matcher(source);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private Map<String, Boolean> flatYamlKeys(Path file) throws IOException {
        Map<String, Boolean> keys = new TreeMap<>();
        List<int[]> indentStack = new ArrayList<>();
        List<String> nameStack = new ArrayList<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (raw.isBlank() || raw.strip().startsWith("#")) {
                continue;
            }
            Matcher m = LINE_PATTERN.matcher(raw);
            if (!m.find()) {
                continue;
            }
            int indent = m.group(1).length();
            String name = m.group(2);
            while (!indentStack.isEmpty() && indentStack.get(indentStack.size() - 1)[0] >= indent) {
                indentStack.remove(indentStack.size() - 1);
                nameStack.remove(nameStack.size() - 1);
            }
            List<String> path = new ArrayList<>(nameStack);
            path.add(name);
            keys.put(String.join(".", path), true);
            boolean hasInlineValue = Pattern.compile(":\\s*\\S").matcher(raw).find();
            if (!hasInlineValue) {
                indentStack.add(new int[]{indent});
                nameStack.add(name);
            }
        }
        return keys;
    }
}
