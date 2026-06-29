package farm.query.vgi.poi;

import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for the per-object discovery/description metadata that the
 * {@code vgi-lint} strict profile (0.26.0) expects on <em>every</em> function
 * and table.
 *
 * <p>Each function/table surfaces these tags:
 * <ul>
 *   <li>{@code vgi.title} (VGI124) — human-friendly display name (must not
 *       normalize-equal the machine name)</li>
 *   <li>{@code vgi.doc_llm} (VGI112) — a Markdown narrative aimed at LLMs/agents</li>
 *   <li>{@code vgi.doc_md} (VGI113) — a Markdown narrative aimed at human docs</li>
 *   <li>{@code vgi.keywords} (VGI126) — comma-separated search terms/synonyms</li>
 *   <li>{@code vgi.source_url} (VGI128) — link to the implementing source file</li>
 * </ul>
 */
final class Meta {

    private Meta() {}

    /** Base GitHub blob URL for source files in this repo (pinned to {@code main}). */
    static final String SOURCE_BASE =
            "https://github.com/Query-farm/vgi-poi/blob/main/src/main/java/farm/query/vgi/poi";

    static final String REPO = "https://github.com/Query-farm/vgi-poi";

    /** Build the implementation {@code vgi.source_url} for a source file. */
    static String sourceUrl(String fileName) {
        return SOURCE_BASE + "/" + fileName;
    }

    /**
     * The standard per-object discovery/description tags as an ordered map.
     *
     * <p>VGI138 requires {@code vgi.keywords} to be a JSON array of strings, so the
     * comma-separated {@code keywords} argument is converted here. VGI139 forbids a
     * per-object {@code vgi.source_url} (it belongs only on the catalog), so the
     * {@code fileName} argument is retained for documentation/callsite clarity but
     * no longer emits a source_url tag.
     *
     * @param title    human display name (add an extra word vs. the machine name)
     * @param docLlm   Markdown narrative for an LLM/agent audience
     * @param docMd    Markdown narrative for human docs (distinct content from docLlm)
     * @param keywords comma-separated search terms / synonyms (emitted as a JSON array)
     * @param fileName implementing source file (relative to the package dir)
     */
    static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords, String fileName) {
        // fileName retained for callsite documentation; no per-object source_url
        // is emitted (VGI139 — source_url lives only on the catalog object).
        assert fileName != null;
        var t = new LinkedHashMap<String, String>();
        t.put("vgi.title", title);
        t.put("vgi.doc_llm", docLlm);
        t.put("vgi.doc_md", docMd);
        t.put("vgi.keywords", keywordsJson(keywords));
        return t;
    }

    /** Convert a comma-separated keyword string into a JSON array of strings (VGI138). */
    static String keywordsJson(String csv) {
        var sb = new StringBuilder("[");
        boolean first = true;
        for (String raw : csv.split(",")) {
            String k = raw.trim();
            if (k.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append('"').append(k.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append("]").toString();
    }

    /** A {@link FunctionExample} with no expected output (the linter executes it). */
    static FunctionExample example(String sql, String description) {
        return new FunctionExample(sql, description, null);
    }

    static List<FunctionExample> examples(FunctionExample... e) {
        return List.of(e);
    }
}
