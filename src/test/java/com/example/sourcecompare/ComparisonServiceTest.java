package com.example.sourcecompare;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonServiceTest {

    @Test
    void diffFileMapsMatchesRenamesByHash() throws Exception {
        Map<String, FileInfo> left = Map.of("a/Foo.java", new FileInfo("a/Foo.java", "class Foo {}\n"));
        Map<String, FileInfo> right = Map.of("a/Bar.java", new FileInfo("a/Bar.java", "class Foo {}\n"));

        ComparisonResult result = invokeDiff(left, right);

        assertTrue(result.getRenamed().stream().anyMatch(r -> "a/Foo.java".equals(r.getFrom()) && "a/Bar.java".equals(r.getTo())));
        assertTrue(result.getAdded().isEmpty(), "Exact hash match should be classified as rename, not addition");
        assertTrue(result.getDeleted().isEmpty(), "Exact hash match should be classified as rename, not deletion");
    }

    @Test
    void diffFileMapsRestrictsRenameCandidatesBySize() throws Exception {
        String leftContent =
                """
                class Foo {
                    void test() {
                        System.out.println(1);
                    }
                }
                """;
        String rightContent =
                """
                class Foo {
                    void test() {
                        System.out.println(1);
                        int value = 42;
                    }
                }
                """;
        Map<String, FileInfo> left = Map.of("pkg/Foo.java", new FileInfo("pkg/Foo.java", leftContent));
        Map<String, FileInfo> right = Map.of("pkg/Bar.java", new FileInfo("pkg/Bar.java", rightContent));

        ComparisonResult result = invokeDiff(left, right);

        assertEquals(1, result.getRenamed().size());
        RenameInfo rename = result.getRenamed().get(0);
        assertEquals("pkg/Foo.java", rename.getFrom());
        assertEquals("pkg/Bar.java", rename.getTo());
    }

    private ComparisonResult invokeDiff(Map<String, FileInfo> left, Map<String, FileInfo> right)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ComparisonService service = new ComparisonService();
        Method method = ComparisonService.class.getDeclaredMethod("diffFileMaps", Map.class, Map.class);
        method.setAccessible(true);
        return (ComparisonResult) method.invoke(service, left, right);
    }
}
