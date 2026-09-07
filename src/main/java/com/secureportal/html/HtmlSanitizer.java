package com.secureportal.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Runs at upload time, not at serve time — the bytes that land in storage
 * are already clean, so there's nothing dangerous to leak even if the
 * delivery endpoint were ever misconfigured. {@code Safelist.relaxed()}
 * never included {@code script}/{@code iframe}/{@code object}/{@code embed}/
 * {@code form} or any {@code on*} event handler attribute in the first
 * place; {@code style} is deliberately left out too, to keep the surface
 * small.
 */
@Component
public class HtmlSanitizer {

    public String sanitize(String rawHtml) {
        Safelist safelist = Safelist.relaxed()
                .addAttributes("img", "width", "height")
                .addAttributes(":all", "class", "id");

        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(rawHtml, "", safelist, outputSettings);
    }
}
