package com.liuyongliang.kindlereader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private final ArrayList<BookEngine.Book> books = new ArrayList<BookEngine.Book>();
    private LinearLayout root;
    private FrameLayout pageArea;
    private TextView pageText;
    private ImageView pdfImage;
    private TextView pageNumber;
    private BookEngine.Book current;
    private String content;
    private final ArrayList<Integer> pageStarts = new ArrayList<Integer>();
    private int pageIndex;
    private int pageCount;
    private int fontSp = 27;
    private int renderToken;
    private static final int SHELF_COLUMNS = 3;
    private static final int SHELF_ROWS = 2;
    private static final int SHELF_PAGE_SIZE = SHELF_COLUMNS * SHELF_ROWS;
    private int shelfPage;
    private LinearLayout shelfGrid;
    private TextView shelfPageNumber;
    private final View.OnTouchListener readingTouch = new View.OnTouchListener() {
        float startX, startY;
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startX = event.getX(); startY = event.getY(); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                if (Math.abs(dx) > dp(65) && Math.abs(dx) > Math.abs(dy)) {
                    turn(dx < 0 ? 1 : -1);
                } else if (Math.abs(dx) < dp(35) && Math.abs(dy) < dp(35)) {
                    if (event.getX() < v.getWidth() * 0.35f) turn(-1);
                    else if (event.getX() > v.getWidth() * 0.65f) turn(1);
                }
                return true;
            }
            return true;
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        fontSp = getPreferences(0).getInt("font", 27);
        showShelf();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.BLACK);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void base() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(Color.BLACK);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return line;
    }

    private void showShelf() {
        renderToken++;
        clearPage();
        current = null;
        content = null;
        shelfGrid = null;
        shelfPageNumber = null;
        base();
        final TextView info = label("正在扫描本地图书…", 18, false);
        info.setGravity(Gravity.CENTER);
        root.addView(info, new LinearLayout.LayoutParams(-1, 0, 1));
        final LinearLayout activeRoot = root;
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<BookEngine.Book> found = BookEngine.scan();
                for (BookEngine.Book book : found) BookEngine.loadCover(book);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (activeRoot != root || current != null) return;
                        books.clear();
                        books.addAll(found);
                        activeRoot.removeView(info);
                        if (books.isEmpty()) {
                            TextView empty = label("暂无图书\n\n把 EPUB、PDF 或 TXT 放入 Documents、Books 或 Download 文件夹，再重新打开书架。", 20, false);
                            empty.setGravity(Gravity.CENTER);
                            empty.setPadding(dp(28), 0, dp(28), 0);
                            activeRoot.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
                        } else {
                            shelfGrid = new LinearLayout(MainActivity.this);
                            shelfGrid.setOrientation(LinearLayout.VERTICAL);
                            activeRoot.addView(shelfGrid, new LinearLayout.LayoutParams(-1, 0, 1));
                        }
                        activeRoot.addView(divider());
                        LinearLayout footer = new LinearLayout(MainActivity.this);
                        footer.setGravity(Gravity.TOP);
                        footer.setPadding(0, dp(3), 0, 0);
                        TextView previous = label("‹ 上一页", 16, false);
                        previous.setGravity(Gravity.CENTER);
                        footer.addView(previous, new LinearLayout.LayoutParams(dp(120), dp(46)));
                        previous.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) { changeShelfPage(-1); }
                        });
                        shelfPageNumber = label("", 16, true);
                        shelfPageNumber.setGravity(Gravity.CENTER);
                        footer.addView(shelfPageNumber, new LinearLayout.LayoutParams(0, dp(46), 1));
                        shelfPageNumber.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                int total = Math.max(1, (books.size() + SHELF_PAGE_SIZE - 1) / SHELF_PAGE_SIZE);
                                shelfPage = (shelfPage + 1) % total;
                                renderShelfPage();
                            }
                        });
                        TextView next = label("下一页 ›", 16, false);
                        next.setGravity(Gravity.CENTER);
                        footer.addView(next, new LinearLayout.LayoutParams(dp(120), dp(46)));
                        next.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) { changeShelfPage(1); }
                        });
                        activeRoot.addView(footer, new LinearLayout.LayoutParams(-1, dp(52)));
                        renderShelfPage();
                    }
                });
            }
        }).start();
    }

    private void changeShelfPage(int direction) {
        int total = Math.max(1, (books.size() + SHELF_PAGE_SIZE - 1) / SHELF_PAGE_SIZE);
        int next = shelfPage + direction;
        if (next >= 0 && next < total) {
            shelfPage = next;
            renderShelfPage();
        }
    }

    private void renderShelfPage() {
        if (shelfGrid == null || shelfPageNumber == null) return;
        int total = Math.max(1, (books.size() + SHELF_PAGE_SIZE - 1) / SHELF_PAGE_SIZE);
        shelfPage = Math.max(0, Math.min(shelfPage, total - 1));
        shelfPageNumber.setText("第 " + (shelfPage + 1) + " / " + total + " 页");
        shelfGrid.removeAllViews();
        for (int row = 0; row < SHELF_ROWS; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < SHELF_COLUMNS; column++) {
                int index = shelfPage * SHELF_PAGE_SIZE + row * SHELF_COLUMNS + column;
                View tile = index < books.size() ? bookTile(index) : new View(this);
                line.addView(tile, new LinearLayout.LayoutParams(0, -1, 1));
            }
            shelfGrid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private View bookTile(final int index) {
        BookEngine.Book book = books.get(index);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.WHITE);
        outline.setStroke(dp(1), Color.BLACK);
        tile.setBackground(outline);
        tile.setPadding(dp(1), dp(1), dp(1), dp(1));
        ImageView cover = new ImageView(this);
        cover.setImageBitmap(book.cover);
        cover.setScaleType(ImageView.ScaleType.FIT_XY);
        cover.setBackgroundColor(Color.WHITE);
        tile.addView(cover, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView title = label(book.title, 14, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(title, new LinearLayout.LayoutParams(-1, dp(43)));
        tile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openBook(books.get(index)); }
        });
        return tile;
    }

    private void openBook(final BookEngine.Book book) {
        current = book;
        content = null;
        pageStarts.clear();
        pageIndex = getPreferences(0).getInt("page:" + book.file.getAbsolutePath(), 0);
        buildReader();
        showLoading("正在打开「" + book.title + "」…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    final String loadedText;
                    final int loadedPages;
                    if ("PDF".equals(book.type)) {
                        loadedText = null;
                        loadedPages = BookEngine.pdfPages(book.file);
                    } else {
                        loadedText = BookEngine.loadText(book);
                        loadedPages = 0;
                    }
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (current != book) return;
                            content = loadedText;
                            pageCount = loadedPages;
                            if (loadedText == null) {
                                pageIndex = Math.min(Math.max(0, pageIndex), pageCount - 1);
                                renderPdfPage();
                            } else {
                                final int savedOffset = getPreferences(0).getInt("offset:" + book.file.getAbsolutePath(), 0);
                                pageArea.post(new Runnable() {
                                    public void run() {
                                        if (current == book) paginate(savedOffset);
                                    }
                                });
                            }
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (current == book) showLoading("无法打开这本书\n" + error.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void buildReader() {
        base();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(7), 0, dp(7), 0);
        TextView back = label("‹ 书架", 19, true);
        back.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(90), dp(57)));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showShelf(); }
        });
        TextView title = label(current.title, 18, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(57), 1));
        if (!"PDF".equals(current.type)) {
            TextView smaller = label("A−", 21, true);
            smaller.setGravity(Gravity.CENTER);
            top.addView(smaller, new LinearLayout.LayoutParams(dp(48), dp(57)));
            smaller.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { changeFont(-2); }
            });
            TextView larger = label("A+", 21, true);
            larger.setGravity(Gravity.CENTER);
            top.addView(larger, new LinearLayout.LayoutParams(dp(48), dp(57)));
            larger.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { changeFont(2); }
            });
        }
        root.addView(top);
        root.addView(divider());
        pageArea = new FrameLayout(this);
        pageArea.setBackgroundColor(Color.WHITE);
        root.addView(pageArea, new LinearLayout.LayoutParams(-1, 0, 1));
        pageArea.setOnTouchListener(readingTouch);
        root.addView(divider());
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.TOP);
        bottom.setPadding(0, dp(3), 0, 0);
        TextView previous = label("‹ 上一页", 16, false);
        previous.setGravity(Gravity.CENTER);
        bottom.addView(previous, new LinearLayout.LayoutParams(dp(120), dp(46)));
        previous.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { turn(-1); }
        });
        pageNumber = label("", 16, false);
        pageNumber.setGravity(Gravity.CENTER);
        bottom.addView(pageNumber, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView next = label("下一页 ›", 16, false);
        next.setGravity(Gravity.CENTER);
        bottom.addView(next, new LinearLayout.LayoutParams(dp(120), dp(46)));
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { turn(1); }
        });
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private void showLoading(String message) {
        clearPage();
        TextView hint = label(message, 19, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(25), 0, dp(25), 0);
        pageArea.addView(hint, new FrameLayout.LayoutParams(-1, -1));
        pageNumber.setText("");
    }

    private void paginate(int wantedOffset) {
        if (content == null || pageArea.getWidth() < dp(100)) return;
        int width = pageArea.getWidth() - dp(32);
        int height = pageArea.getHeight() - dp(24);
        if (height < dp(100)) return;
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(fontSp * getResources().getDisplayMetrics().scaledDensity);
        StaticLayout layout = new StaticLayout(content, paint, width, Layout.Alignment.ALIGN_NORMAL,
            1.25f, dp(2), false);
        pageStarts.clear();
        pageStarts.add(0);
        int startLine = 0;
        for (int line = 0; line < layout.getLineCount(); line++) {
            if (layout.getLineBottom(line) - layout.getLineTop(startLine) > height && line > startLine) {
                int next = layout.getLineStart(line);
                if (next > pageStarts.get(pageStarts.size() - 1)) pageStarts.add(next);
                startLine = line;
            }
        }
        pageStarts.add(content.length());
        pageCount = pageStarts.size() - 1;
        pageIndex = 0;
        for (int i = 0; i < pageCount; i++) {
            if (pageStarts.get(i) <= wantedOffset) pageIndex = i;
            else break;
        }
        clearPage();
        pageText = label("", fontSp, false);
        pageText.setGravity(Gravity.TOP | Gravity.LEFT);
        pageText.setLineSpacing(dp(2), 1.25f);
        pageText.setPadding(dp(16), dp(10), dp(16), dp(10));
        pageText.setTextIsSelectable(false);
        pageArea.addView(pageText, new FrameLayout.LayoutParams(-1, -1));
        pageText.setOnTouchListener(readingTouch);
        renderTextPage();
    }

    private void renderTextPage() {
        if (content == null || pageStarts.size() < 2 || pageText == null) return;
        pageIndex = Math.max(0, Math.min(pageIndex, pageCount - 1));
        int start = pageStarts.get(pageIndex);
        int end = pageStarts.get(pageIndex + 1);
        pageText.setText(content.substring(start, end));
        updateFooter();
        getPreferences(0).edit()
            .putInt("offset:" + current.file.getAbsolutePath(), start)
            .putInt("page:" + current.file.getAbsolutePath(), pageIndex).apply();
    }

    private void renderPdfPage() {
        if (current == null || !"PDF".equals(current.type)) return;
        final BookEngine.Book book = current;
        final int index = pageIndex;
        final int token = ++renderToken;
        final int width = Math.max(1, pageArea.getWidth());
        final int height = Math.max(1, pageArea.getHeight());
        showLoading("正在绘制第 " + (index + 1) + " 页…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    final Bitmap bitmap = BookEngine.renderPdf(book.file, index, width, height);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (current != book || pageIndex != index || renderToken != token) {
                                bitmap.recycle(); return;
                            }
                            clearPage();
                            pdfImage = new ImageView(MainActivity.this);
                            pdfImage.setImageBitmap(bitmap);
                            pdfImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            pageArea.addView(pdfImage, new FrameLayout.LayoutParams(-1, -1));
                            pdfImage.setOnTouchListener(readingTouch);
                            updateFooter();
                            getPreferences(0).edit().putInt("page:" + book.file.getAbsolutePath(), index).apply();
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (current == book && pageIndex == index) showLoading("PDF 绘制失败\n" + error.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void updateFooter() {
        pageNumber.setText("第 " + (pageIndex + 1) + " / " + pageCount + " 页");
    }

    private void clearPage() {
        if (pageArea == null) return;
        Bitmap old = null;
        if (pdfImage != null && pdfImage.getDrawable() instanceof BitmapDrawable) {
            old = ((BitmapDrawable) pdfImage.getDrawable()).getBitmap();
            pdfImage.setImageDrawable(null);
            pdfImage = null;
        }
        pageArea.removeAllViews();
        if (old != null && !old.isRecycled()) old.recycle();
    }

    private void turn(int direction) {
        if (current == null || pageCount <= 0) return;
        int next = pageIndex + direction;
        if (next < 0 || next >= pageCount) return;
        pageIndex = next;
        if ("PDF".equals(current.type)) renderPdfPage();
        else renderTextPage();
    }

    private void changeFont(int delta) {
        if (content == null) return;
        int next = Math.max(19, Math.min(39, fontSp + delta));
        if (next == fontSp) return;
        int offset = pageStarts.size() > pageIndex ? pageStarts.get(pageIndex) : 0;
        fontSp = next;
        getPreferences(0).edit().putInt("font", fontSp).apply();
        paginate(offset);
    }

    @Override public void onBackPressed() {
        if (current != null) showShelf();
        else super.onBackPressed();
    }
}
