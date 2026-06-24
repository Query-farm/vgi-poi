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
     * The five standard per-object discovery/description tags as an ordered map.
     *
     * @param title    human display name (add an extra word vs. the machine name)
     * @param docLlm   Markdown narrative for an LLM/agent audience
     * @param docMd    Markdown narrative for human docs (distinct content from docLlm)
     * @param keywords comma-separated search terms / synonyms
     * @param fileName implementing source file (relative to the package dir)
     */
    static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords, String fileName) {
        var t = new LinkedHashMap<String, String>();
        t.put("vgi.title", title);
        t.put("vgi.doc_llm", docLlm);
        t.put("vgi.doc_md", docMd);
        t.put("vgi.keywords", keywords);
        t.put("vgi.source_url", sourceUrl(fileName));
        return t;
    }

    /** A {@link FunctionExample} with no expected output (the linter executes it). */
    static FunctionExample example(String sql, String description) {
        return new FunctionExample(sql, description, null);
    }

    static List<FunctionExample> examples(FunctionExample... e) {
        return List.of(e);
    }
}
