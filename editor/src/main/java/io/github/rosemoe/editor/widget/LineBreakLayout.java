/*
 *   Copyright 2020 Rosemoe
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.github.rosemoe.editor.widget;

import android.graphics.Paint;

import java.util.NoSuchElementException;

import io.github.rosemoe.editor.text.Content;
import io.github.rosemoe.editor.text.ContentLine;
import io.github.rosemoe.editor.text.FontCache;
import io.github.rosemoe.editor.util.BinaryHeap;
import io.github.rosemoe.editor.util.IntPair;

/**
 * Layout implementation of editor
 * This layout is never broke unless there is actually a newline character
 *
 * @author Rose
 */
class LineBreakLayout implements Layout {

    private final CodeEditor editor;
    private final Paint shadowPaint;
    private Content text;
    private FontCache fontCache;
    private BinaryHeap widthMaintainer;

    LineBreakLayout(CodeEditor editor, Content text) {
        this.editor = editor;
        this.text = text;
        shadowPaint = new Paint(editor.getTextPaint());
        fontCache = new FontCache();
        measureAllLines();
    }

    private void measureAllLines() {
        if (text == null) {
            return;
        }
        widthMaintainer = new BinaryHeap();
        widthMaintainer.ensureCapacity(text.getLineCount());
        for (int i = 0; i < text.getLineCount(); i++) {
            ContentLine line = text.getLine(i);
            int width = (int) fontCache.measureText(line, 0, line.length(), shadowPaint);
            line.setWidth(width);
            line.setId(widthMaintainer.push(width));
        }
    }

    private void measureLines(int startLine, int endLine) {
        if (text == null) {
            return;
        }
        while (startLine <= endLine && startLine < text.getLineCount()) {
            ContentLine line = text.getLine(startLine);
            int width = (int) fontCache.measureText(line, 0, line.length(), shadowPaint);
            if (line.getId() != -1) {
                if (line.getWidth() == width) {
                    startLine++;
                    continue;
                }
                widthMaintainer.update(line.getId(), width);
                startLine++;
                continue;
            }
            line.setId(widthMaintainer.push(width));
            line.setWidth(width);
            startLine++;
        }
    }

    private float[] orderedFindCharIndex(float targetOffset, CharSequence str) {
        float width = 0f;
        int index = 0;
        int length = str.length();
        while (index < length && width < targetOffset) {
            float single = fontCache.measureChar(str.charAt(index), shadowPaint);
            if (str.charAt(index) == '\t') {
                single *= editor.getTabWidth();
            }
            width += single;
            index++;
        }
        return new float[]{index, width};
    }

    @Override
    public RowIterator obtainRowIterator(int initialRow) {
        return new LineBreakLayoutRowItr(initialRow);
    }

    @Override
    public void beforeReplace(Content content) {
        // Intentionally empty
    }

    @Override
    public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
        measureLines(startLine, endLine);
    }

    @Override
    public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) {
        measureLines(startLine, startLine);
    }

    @Override
    public void onRemove(Content content, ContentLine line) {
        widthMaintainer.remove(line.getId());
    }

    @Override
    public void destroyLayout() {
        widthMaintainer = null;
        fontCache = null;
        text = null;
    }

    @Override
    public int getLineNumberForRow(int row) {
        return row;
    }

    @Override
    public int getLayoutWidth() {
        return widthMaintainer.top();
    }

    @Override
    public int getLayoutHeight() {
        return text.getLineCount() * editor.getRowHeight();
    }

    @Override
    public long getCharPositionForLayoutOffset(float xOffset, float yOffset) {
        int lineCount = text.getLineCount();
        int line = Math.min(lineCount - 1, Math.max((int) (yOffset / editor.getRowHeight()), 0));
        ContentLine str = text.getLine(line);
        float[] res = orderedFindCharIndex(xOffset, str);
        if (res[1] < xOffset) {
            res[0]++;
        }
        res[0] = Math.min(str.length(), res[0]);
        return IntPair.pack(line, (int) res[0]);
    }

    @Override
    public float[] getCharLayoutOffset(int line, int column) {
        CharSequence sequence = text.getLine(line);
        return new float[]{
                editor.getRowHeight() * (line + 1),
                fontCache.measureText(sequence, 0, column, shadowPaint)
        };
    }

    class LineBreakLayoutRowItr implements RowIterator {

        private int currentRow;
        private Row result;

        LineBreakLayoutRowItr(int initialRow) {
            currentRow = initialRow;
            result = new Row();
            result.isLeadingRow = true;
            result.startColumn = 0;
        }

        @Override
        public Row next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            result.lineIndex = currentRow;
            result.endColumn = text.getColumnCount(currentRow++);
            return result;
        }

        @Override
        public boolean hasNext() {
            return currentRow >= 0 && currentRow < text.getLineCount();
        }

    }

}
