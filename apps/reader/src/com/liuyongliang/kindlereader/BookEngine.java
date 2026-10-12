package com.liuyongliang.kindlereader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class BookEngine {
    static final int COVER_W = 180;
    static final int COVER_H = 238;

    static final class Book {
        final File file;
        final String type;
        String title;
        Bitmap cover;

        Book(File file, String type) {
            this.file = file;
            this.type = type;
            String name = file.getName();
            this.title = name.substring(0, name.lastIndexOf('.'));
        }
    }

    private static final class EpubInfo {
        String opf;
        String title;
        String coverId;
        String coverHref;
        final Map<String, String> manifest = new HashMap<String, String>();
        final ArrayList<String> spine = new ArrayList<String>();
    }

    private BookEngine() { }

    static ArrayList<Book> scan() {
        ArrayList<Book> books = new ArrayList<Book>();
        HashSet<String> seen = new HashSet<String>();
        String storage = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String[] folders = {"Documents", "Books", "Download", "Reader", "WLANImport"};
        for (String folder : folders) scanFolder(new File(storage, folder), 0, books, seen);
        Collections.sort(books, new Comparator<Book>() {
            public int compare(Book a, Book b) {
                int first = a.title.compareToIgnoreCase(b.title);
                return first != 0 ? first : a.file.getAbsolutePath().compareTo(b.file.getAbsolutePath());
            }
        });
        return books;
    }

    private static void scanFolder(File dir, int depth, ArrayList<Book> books, HashSet<String> seen) {
        if (depth > 3 || books.size() >= 400 || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (books.size() >= 400) return;
            if (file.isDirectory()) {
                if (!file.getName().startsWith(".") && !file.getName().endsWith(".sdr")) {
                    scanFolder(file, depth + 1, books, seen);
                }
            } else {
                String name = file.getName().toLowerCase(Locale.US);
                String type = name.endsWith(".epub") ? "EPUB" :
                    name.endsWith(".pdf") ? "PDF" : name.endsWith(".txt") ? "TXT" : null;
                if (type != null && file.length() >= 128 && seen.add(file.getAbsolutePath())) {
                    books.add(new Book(file, type));
                }
            }
        }
    }

    static void loadCover(Book book) {
        try {
            if ("EPUB".equals(book.type)) {
                ZipFile zip = new ZipFile(book.file);
                try {
                    EpubInfo info = readEpubInfo(zip);
                    if (info.title != null && info.title.trim().length() > 0) book.title = info.title.trim();
                    if (info.coverHref != null) {
                        ZipEntry entry = zip.getEntry(resolve(info.opf, info.coverHref));
                        if (entry != null) {
                            InputStream in = zip.getInputStream(entry);
                            try {
                                byte[] raw = readAll(in, 8 * 1024 * 1024);
                                BitmapFactory.Options bounds = new BitmapFactory.Options();
                                bounds.inJustDecodeBounds = true;
                                BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
                                BitmapFactory.Options opts = new BitmapFactory.Options();
                                opts.inSampleSize = Math.max(1, Math.max(bounds.outWidth / 360, bounds.outHeight / 476));
                                book.cover = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
                            } finally { in.close(); }
                        }
                    }
                } finally { zip.close(); }
            } else if ("PDF".equals(book.type)) {
                book.cover = renderPdf(book.file, 0, COVER_W, COVER_H);
            }
        } catch (Exception ignored) { }
        if (book.cover == null) book.cover = textCover(book.title);
    }

    static Bitmap renderPdf(File file, int index, int width, int height) throws IOException {
        ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer = null;
        try {
            renderer = new PdfRenderer(fd);
            if (index < 0 || index >= renderer.getPageCount()) throw new IOException("PDF 页码无效");
            PdfRenderer.Page page = renderer.openPage(index);
            try {
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);
                float scale = Math.min((float) width / page.getWidth(), (float) height / page.getHeight());
                int w = Math.max(1, Math.round(page.getWidth() * scale));
                int h = Math.max(1, Math.round(page.getHeight() * scale));
                Rect target = new Rect((width - w) / 2, (height - h) / 2,
                    (width - w) / 2 + w, (height - h) / 2 + h);
                page.render(bitmap, target, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            } finally { page.close(); }
        } finally {
            if (renderer != null) renderer.close();
            else fd.close();
        }
    }

    static int pdfPages(File file) throws IOException {
        ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer = null;
        try {
            renderer = new PdfRenderer(fd);
            return renderer.getPageCount();
        } finally {
            if (renderer != null) renderer.close();
            else fd.close();
        }
    }

    static String loadText(Book book) throws Exception {
        if ("TXT".equals(book.type)) {
            InputStream in = new FileInputStream(book.file);
            try {
                byte[] raw = readAll(in, 16 * 1024 * 1024);
                if (raw.length >= 2 && (raw[0] & 255) == 0xff && (raw[1] & 255) == 0xfe) {
                    return new String(raw, 2, raw.length - 2, "UTF-16LE");
                }
                if (raw.length >= 2 && (raw[0] & 255) == 0xfe && (raw[1] & 255) == 0xff) {
                    return new String(raw, 2, raw.length - 2, "UTF-16BE");
                }
                try {
                    return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(raw)).toString().replace("\uFEFF", "");
                } catch (CharacterCodingException ex) {
                    return new String(raw, "GB18030");
                }
            } finally { in.close(); }
        }
        ZipFile zip = new ZipFile(book.file);
        try {
            EpubInfo info = readEpubInfo(zip);
            StringBuilder text = new StringBuilder();
            for (String id : info.spine) {
                String href = info.manifest.get(id);
                if (href == null) continue;
                ZipEntry entry = zip.getEntry(resolve(info.opf, href));
                if (entry == null) continue;
                InputStream in = zip.getInputStream(entry);
                String html;
                try { html = new String(readAll(in, 4 * 1024 * 1024), "UTF-8"); }
                finally { in.close(); }
                html = html.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "")
                    .replaceAll("(?is)<style\\b[^>]*>.*?</style>", "");
                int body = html.toLowerCase(Locale.US).indexOf("<body");
                if (body >= 0) {
                    int start = html.indexOf('>', body);
                    int end = html.toLowerCase(Locale.US).lastIndexOf("</body>");
                    if (start >= 0) html = html.substring(start + 1, end > start ? end : html.length());
                }
                String chapter = Html.fromHtml(html).toString().trim();
                if (chapter.length() > 0) text.append(chapter).append("\n\n");
                if (text.length() > 8 * 1024 * 1024) break;
            }
            if (text.length() == 0) throw new IOException("EPUB 中没有可读文字");
            return text.toString();
        } finally { zip.close(); }
    }

    private static EpubInfo readEpubInfo(ZipFile zip) throws Exception {
        ZipEntry container = zip.getEntry("META-INF/container.xml");
        if (container == null) throw new IOException("缺少 EPUB 目录");
        EpubInfo info = new EpubInfo();
        InputStream in = zip.getInputStream(container);
        try {
            XmlPullParser xml = Xml.newPullParser();
            xml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xml.setInput(in, "UTF-8");
            for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
                if (event == XmlPullParser.START_TAG && "rootfile".equals(xml.getName())) {
                    info.opf = xml.getAttributeValue(null, "full-path");
                    break;
                }
            }
        } finally { in.close(); }
        if (info.opf == null) throw new IOException("找不到 EPUB 清单");
        ZipEntry opf = zip.getEntry(info.opf);
        if (opf == null) throw new IOException("EPUB 清单不存在");
        in = zip.getInputStream(opf);
        try {
            XmlPullParser xml = Xml.newPullParser();
            xml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xml.setInput(in, "UTF-8");
            for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
                if (event != XmlPullParser.START_TAG) continue;
                String tag = xml.getName();
                if ("item".equals(tag)) {
                    String id = xml.getAttributeValue(null, "id");
                    String href = xml.getAttributeValue(null, "href");
                    if (id != null && href != null) info.manifest.put(id, href);
                    String props = xml.getAttributeValue(null, "properties");
                    if (props != null && props.contains("cover-image")) info.coverHref = href;
                    if (info.coverHref == null && href != null && href.toLowerCase(Locale.US).contains("cover")
                        && (href.endsWith(".jpg") || href.endsWith(".jpeg") || href.endsWith(".png"))) {
                        info.coverHref = href;
                    }
                } else if ("itemref".equals(tag)) {
                    String idref = xml.getAttributeValue(null, "idref");
                    if (idref != null) info.spine.add(idref);
                } else if ("meta".equals(tag)) {
                    if ("cover".equals(xml.getAttributeValue(null, "name"))) {
                        info.coverId = xml.getAttributeValue(null, "content");
                    }
                } else if ("dc:title".equals(tag) || "title".equals(tag)) {
                    info.title = xml.nextText();
                }
            }
        } finally { in.close(); }
        if (info.coverHref == null && info.coverId != null) info.coverHref = info.manifest.get(info.coverId);
        return info;
    }

    private static String resolve(String opf, String href) throws Exception {
        java.net.URI base = new java.net.URI(opf.replace(" ", "%20"));
        java.net.URI path = base.resolve(new java.net.URI(href.replace(" ", "%20"))).normalize();
        return java.net.URLDecoder.decode(path.getPath(), "UTF-8");
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int size;
        while ((size = in.read(chunk)) != -1) {
            if (out.size() + size > max) throw new IOException("文件过大");
            out.write(chunk, 0, size);
        }
        return out.toByteArray();
    }

    private static Bitmap textCover(String title) {
        Bitmap bitmap = Bitmap.createBitmap(COVER_W, COVER_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        pen.setColor(Color.BLACK);
        pen.setStyle(Paint.Style.FILL);
        pen.setTextSize(23);
        pen.setFakeBoldText(true);
        ArrayList<String> lines = new ArrayList<String>();
        int at = 0;
        while (at < title.length() && lines.size() < 4) {
            int count = pen.breakText(title, at, title.length(), true, COVER_W - 24, null);
            if (count <= 0) break;
            lines.add(title.substring(at, at + count));
            at += count;
        }
        int firstBaseline = (COVER_H - lines.size() * 31) / 2 + 24;
        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(lines.get(i), 12, firstBaseline + i * 31, pen);
        }
        return bitmap;
    }
}
