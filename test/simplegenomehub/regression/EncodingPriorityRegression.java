package simplegenomehub.regression;

import java.nio.charset.*;
import java.nio.file.*;
import simplegenomehub.util.fileio.JsonMetadataSupport;

/** A decoder that accepts the bytes must retain the documented native/default priority. */
public final class EncodingPriorityRegression {
    public static void main(String[] args) throws Exception {
        if (!Charset.defaultCharset().equals(Charset.forName("windows-1252")))
            throw new AssertionError("Run this fixture with -Dfile.encoding=windows-1252");
        Path root = Files.createTempDirectory("sgh-encoding-priority-");
        String previous = System.getProperty("native.encoding");
        try {
            System.setProperty("native.encoding", "not-an-installed-charset");
            Path western = root.resolve("western.json");
            Files.write(western, "{\"title\":\"café résumé\"}".getBytes(Charset.forName("windows-1252")));
            check("café résumé".equals(JsonMetadataSupport.read(western.toFile()).get("title").getAsString()), "Default charset was ignored");
            Path utf8 = root.resolve("utf8.json");
            Files.write(utf8, "{\"title\":\"中文实验\"}".getBytes(StandardCharsets.UTF_8));
            check("中文实验".equals(JsonMetadataSupport.read(utf8.toFile()).get("title").getAsString()), "UTF-8 lost priority");
            System.setProperty("native.encoding", "GBK");
            Path nativeText = root.resolve("native.json");
            Files.write(nativeText, "{\"title\":\"中文实验\"}".getBytes(Charset.forName("GBK")));
            check("中文实验".equals(JsonMetadataSupport.read(nativeText.toFile()).get("title").getAsString()), "Native encoding did not precede the default");
        } finally {
            if (previous == null) System.clearProperty("native.encoding"); else System.setProperty("native.encoding", previous);
        }
        System.out.println("PASS encoding priority: UTF-8, explicit native GBK and Windows-1252 default");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
