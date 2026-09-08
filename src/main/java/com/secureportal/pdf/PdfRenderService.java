package com.secureportal.pdf;

import com.secureportal.config.AppProperties;
import com.secureportal.content.ContentItem;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renders one PDF page to a JPEG on request. The PDF binary itself never
 * reaches the browser — only these rendered images do — which is what makes
 * this a real content-protection boundary rather than a deterrent: there is
 * no file for a viewer to reconstruct, unlike a client-side viewer where the
 * whole PDF sits in browser memory.
 *
 * <p>The rendered page (pre-watermark) is cached in storage since rendering
 * is the expensive step; the watermark itself is applied fresh on every
 * request since it carries the requesting viewer's identity and must never
 * be shared across viewers.
 */
@Service
public class PdfRenderService {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderService.class);

    private final StorageService storageService;
    private final AppProperties appProperties;

    public PdfRenderService(StorageService storageService, AppProperties appProperties) {
        this.storageService = storageService;
        this.appProperties = appProperties;
    }

    public byte[] renderPage(ContentItem item, int pageNumber, String watermarkText) {
        validatePageNumber(item, pageNumber);
        byte[] rendered = getOrRenderPage(item, pageNumber);
        return applyWatermark(rendered, watermarkText);
    }

    private void validatePageNumber(ContentItem item, int pageNumber) {
        if (pageNumber < 1 || (item.getPageCount() != null && pageNumber > item.getPageCount())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such page");
        }
    }

    private byte[] getOrRenderPage(ContentItem item, int pageNumber) {
        String cacheKey = "derived/" + item.getId() + "/p" + pageNumber + ".jpg";

        if (storageService.exists(cacheKey)) {
            try (StorageObject cached = storageService.get(cacheKey, null, null)) {
                return cached.content().readAllBytes();
            } catch (IOException e) {
                log.warn("Failed reading cached render {}, re-rendering", cacheKey, e);
            }
        }

        byte[] rendered = renderFromPdf(item, pageNumber);

        try {
            storageService.put(cacheKey, new ByteArrayInputStream(rendered), rendered.length, "image/jpeg");
        } catch (RuntimeException e) {
            log.warn("Could not cache rendered page {}", cacheKey, e);
        }

        return rendered;
    }

    private byte[] renderFromPdf(ContentItem item, int pageNumber) {
        try (StorageObject source = storageService.get(item.getStorageKey(), null, null);
             PDDocument doc = Loader.loadPDF(source.content().readAllBytes())) {

            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(
                    pageNumber - 1, appProperties.getPdfRenderDpi(), ImageType.RGB);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not render PDF page " + pageNumber + " for " + item.getId(), e);
        }
    }

    /**
     * Tiles the watermark across a 2D grid, in the pre-rotation coordinate
     * space, generously past every edge of the page in both directions —
     * rotating a full-page-sized grid by 30° sweeps its corners well outside
     * the original width/height on every side, so a grid needs to already
     * cover that full rotated extent before rotation, not just the page's
     * own dimensions. (An earlier version tiled only a single fixed X
     * position down the Y axis, which after rotation produced a watermark
     * clustered toward the left edge instead of covering the page.)
     */
    private byte[] applyWatermark(byte[] jpegBytes, String watermarkText) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpegBytes));
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 40));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(18, image.getWidth() / 28)));

            int textWidth = g.getFontMetrics().stringWidth(watermarkText);
            int stepX = textWidth + 70;
            int stepY = Math.max(60, image.getHeight() / 5);
            int span = image.getWidth() + image.getHeight();

            AffineTransform original = g.getTransform();
            g.rotate(-Math.PI / 6, image.getWidth() / 2.0, image.getHeight() / 2.0);

            for (int y = -span; y < span; y += stepY) {
                for (int x = -span; x < span; x += stepX) {
                    g.drawString(watermarkText, x, y);
                }
            }
            g.setTransform(original);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not watermark rendered page", e);
        }
    }
}
