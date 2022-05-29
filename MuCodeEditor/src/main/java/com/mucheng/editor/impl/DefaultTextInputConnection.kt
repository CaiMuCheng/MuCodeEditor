/*
 *
 *  * Copyright (c) 2022 SuMuCheng
 *  *
 *  * CN:
 *  * 作者：SuMuCheng
 *  * Github 主页：https://github.com/CaiMuCheng
 *  *
 *  * 你可以免费使用、商用以下代码，也可以基于以下代码做出修改，但是必须在你的项目中标注出处
 *  * 例如：在你 APP 的设置中添加 “关于编辑器” 一栏，其中标注作者以及此编辑器的 Github 主页
 *  *
 *  * 此代码使用 MPL 2.0 开源许可证，你必须标注作者信息
 *  * 若你要修改文件，请勿删除此注释
 *  * 若你违反以上条例我们有权向您提起诉讼!
 *  *
 *  * EN:
 *  * Author: SuMuCheng
 *  * Github Homepage: https://github.com/CaiMuCheng
 *  *
 *  * You can use the following code for free, commercial use, or make modifications based on the following code, but you must mark the source in your project.
 *  * For example: add an "About Editor" column in your app's settings, which identifies the author and the Github home page of this editor.
 *  *
 *  * This code uses the MPL 2.0 open source license, you must mark the author information
 *  * Do not delete this comment if you want to modify the file.
 *  *
 *  * If you violate the above regulations we have the right to sue you!
 *
 */

package com.mucheng.editor.impl

import android.view.KeyEvent
import com.mucheng.editor.component.Cursor
import com.mucheng.editor.event.TextInputConnection
import com.mucheng.editor.position.RangePosition
import com.mucheng.editor.text.ContentProvider
import com.mucheng.editor.text.LineContent
import com.mucheng.editor.util.execCursorAnimationIfNeeded
import com.mucheng.editor.views.MuCodeEditor

class DefaultTextInputConnection(private val editor: MuCodeEditor) : TextInputConnection {

    private val lexCoroutine by lazy { editor.getLexCoroutine() }

    override fun onCommit(text: CharSequence) {
        val contentProvider = editor.getContentProvider()
        val cursor = contentProvider.getCursor()
        val cursorAnimation = editor.getController().style.cursorAnimation

        if (text.indexOf('\n') != -1 && text.length > 1) {
            val lineContents = text.split('\n')
            execCursorAnimationIfNeeded(cursorAnimation, editor) {
                lineContents.forEachIndexed { index, it ->
                    onCommitInternal(it, contentProvider, cursor)
                    if (index != lineContents.size - 1) {
                        onCommitInternal("\n", contentProvider, cursor)
                    }
                }
                editor.getController().state.lex(
                    lexCoroutine
                )
            }
            editor.showCodeAutoCompletionPanel()
            editor.scrollToColumn(cursor.column)
            return
        }

        execCursorAnimationIfNeeded(cursorAnimation, editor) {
            onCommitInternal(text, contentProvider, cursor)
            // 更新高亮
            editor.getController().state.lex(
                lexCoroutine
            )
        }
        editor.showCodeAutoCompletionPanel()
        editor.scrollToColumn(cursor.column)
    }

    private fun onCommitInternal(
        charSequence: CharSequence,
        contentProvider: ContentProvider,
        cursor: Cursor,
    ) {
        val cursorColumn = cursor.column
        val cursorRow = cursor.row
        val lineContent = contentProvider.getLineContent(cursorColumn)
        val contentSize = lineContent.length

        if (charSequence == "\n" && cursorRow == contentSize) {
            ++cursor.column
            cursor.row = 0
            contentProvider.insertLineContent(cursor.column, LineContent(""))
            return
        }

        if (charSequence == "\n" && cursorRow < contentSize) {
            val subText = lineContent.substring(cursorRow, contentSize)
            lineContent.delete(cursorRow, contentSize)
            ++cursor.column
            cursor.row = 0
            contentProvider.insertLineContent(cursor.column, LineContent(subText))
            return
        }

        if (cursorRow == 0 && contentSize > 0) {
            lineContent.insert(cursorRow, charSequence)
            cursor.row += charSequence.length
            return
        }

        if (cursorRow == 0 && contentSize == 0) {
            lineContent.append(charSequence)
            cursor.row += charSequence.length
            return
        }

        if (cursorRow < contentSize) {
            lineContent.insert(cursorRow, charSequence)
            cursor.row += charSequence.length
            return
        }

        lineContent.append(charSequence)
        cursor.row += charSequence.length
    }

    override fun onDelete() {
        val contentProvider = editor.getContentProvider()
        val cursor = contentProvider.getCursor()

        val cursorAnimation = editor.getController().style.cursorAnimation
        execCursorAnimationIfNeeded(cursorAnimation, editor) {
            val startColumn = cursor.column
            onDeleteInternal(contentProvider, cursor)
            // 更新高亮
            editor.getController().state.lex(lexCoroutine)
        }
        editor.dismissCodeAutoCompletionPanel()
        editor.scrollToColumn(cursor.column)
    }

    override fun onSelectionTextReplace(text: CharSequence) {
        val controller = editor.getController()
        val styleController = controller.style
        val stateController = controller.state
        val selectionRange = stateController.selectionRange
        val cursor = editor.getContentProvider().getCursor()
        execCursorAnimationIfNeeded(styleController.cursorAnimation, editor) {
            onSelectionTextReplaceInternal(text,
                selectionRange!!,
                cursor)
            editor.getController().state.lex(lexCoroutine)
            stateController.unselectText()
        }
        editor.dismissCodeAutoCompletionPanel()
        editor.scrollToColumn(cursor.column)
    }

    private fun onSelectionTextReplaceInternal(
        text: CharSequence,
        selectionRange: RangePosition,
        cursor: Cursor,
    ) {
        val startPos = selectionRange.startPosition
        val endPos = selectionRange.endPosition
        val startColumn = startPos.column
        val endColumn = endPos.column
        val contentProvider = editor.getContentProvider()

        if (startColumn == endColumn && text.indexOf('\n') == -1) {
            val lineContent = editor.getContentProvider().getLineContent(startColumn)
            lineContent.delete(startPos.row, endPos.row)
            lineContent.insert(startPos.row, text)
            cursor.row = startPos.row + text.length
            return
        }

        if (startColumn == endColumn && text.indexOf('\n') != -1) {
            val lineContent = editor.getContentProvider().getLineContent(startColumn)
            lineContent.delete(startPos.row, endPos.row)
            cursor.row = startPos.row
            val lineContents = text.split('\n')
            lineContents.forEachIndexed { index, it ->
                onCommitInternal(it, contentProvider, cursor)
                if (index != lineContents.size - 1) {
                    onCommitInternal("\n", contentProvider, cursor)
                }
            }
            return
        }

        onSelectionTextDeleteInternal(selectionRange, cursor)
        val lineContents = text.split('\n')
        lineContents.forEachIndexed { index, it ->
            onCommitInternal(it, contentProvider, cursor)
            if (index != lineContents.size - 1) {
                onCommitInternal("\n", contentProvider, cursor)
            }
        }

    }

    override fun onSelectionTextDelete() {
        val controller = editor.getController()
        val styleController = controller.style
        val stateController = controller.state
        val selectionRange = stateController.selectionRange
        val cursor = editor.getContentProvider().getCursor()
        execCursorAnimationIfNeeded(styleController.cursorAnimation, editor) {
            onSelectionTextDeleteInternal(selectionRange!!, cursor)
            editor.getController().state.lex(lexCoroutine)
            stateController.unselectText()
        }
        editor.dismissCodeAutoCompletionPanel()
        editor.scrollToColumn(cursor.column)
    }

    private fun onSelectionTextDeleteInternal(selectionRange: RangePosition, cursor: Cursor) {
        val contentProvider = editor.getContentProvider()
        val startPos = selectionRange.startPosition
        val endPos = selectionRange.endPosition
        val startColumn = startPos.column
        val endColumn = endPos.column

        if (startColumn == endColumn) {
            val lineContent = contentProvider.getLineContent(startColumn)
            lineContent.delete(startPos.row, endPos.row)
            cursor.row = startPos.row
            return
        }

        val startLineContent = contentProvider.getLineContent(startColumn)
        val endLineContent = contentProvider.getLineContent(endColumn)

        startLineContent.delete(startPos.row, startLineContent.length)

        if (startColumn + 1 == endColumn) {
            endLineContent.delete(0, endPos.row)
            contentProvider.remove(endLineContent)
            startLineContent.append(endLineContent)

            cursor.column = startPos.column
            cursor.row = startPos.row
            return
        }

        contentProvider.removeRangeLineContent(startColumn + 1, endColumn)
        endLineContent.delete(0, endPos.row)
        contentProvider.remove(endLineContent)
        startLineContent.append(endLineContent)

        cursor.column = startPos.column
        cursor.row = startPos.row
    }

    private fun onDeleteInternal(
        contentProvider: ContentProvider,
        cursor: Cursor,
    ) {
        val cursorColumn = cursor.column
        val cursorRow = cursor.row
        val lineContent = contentProvider.getLineContent(cursorColumn)

        if (cursorRow == 0 && cursorColumn > 1) {
            contentProvider.remove(lineContent)
            --cursor.column
            cursor.row = contentProvider.getColumnRowCount(cursor.column)
            contentProvider.getLineContent(cursor.column).append(lineContent)
            return
        }

        if (cursorRow == 0 && cursorColumn == 1) {
            return
        }

        --cursor.row
        lineContent.deleteCharAt(cursor.row)
    }

    override fun toCursorLeft() {
        val cursorAnimation = editor.getController().style.cursorAnimation
        val cursor = editor.getContentProvider().getCursor()
        execCursorAnimationIfNeeded(cursorAnimation, editor) {
            toCursorLeftInternal()
        }
        editor.scrollToColumn(cursor.column)
    }

    private fun toCursorLeftInternal() {
        val contentProvider = editor.getContentProvider()
        val cursor = contentProvider.getCursor()
        val cursorColumn = cursor.column
        val cursorRow = cursor.row

        if (cursorRow == 0 && cursorColumn == 1) {
            return
        }

        if (cursorRow == 0 && cursorColumn > 1) {
            --cursor.column
            cursor.row = contentProvider.getColumnRowCount(cursor.column)
            return
        }

        --cursor.row
    }

    override fun toCursorRight() {
        val cursorAnimation = editor.getController().style.cursorAnimation
        val cursor = editor.getContentProvider().getCursor()
        execCursorAnimationIfNeeded(cursorAnimation, editor) {
            toCursorRightInternal()
        }
        editor.scrollToColumn(cursor.column)
    }

    override fun toCursorTop() {
        val cursorAnimation = editor.getController().style.cursorAnimation
        val cursor = editor.getContentProvider().getCursor()
        execCursorAnimationIfNeeded(cursorAnimation, editor) {
            toCursorTopInternal()
        }
        editor.scrollToColumn(cursor.column)
    }

    private fun toCursorTopInternal() {
        val contentProvider = editor.getContentProvider()
        val cursor = contentProvider.getCursor()
        val cursorColumn = cursor.column
        val cursorRow = cursor.row

        if (cursorColumn == 1) {
            return
        }

        if (cursorRow == 0 && cursorColumn > 1) {
            --cursor.column
            cursor.row = 0
            return
        }

        --cursor.column
        val beforeContentSize = contentProvider.getColumnRowCount(cursor.column)
        if (cursorRow < beforeContentSize) {
            return
        }

        if (cursorRow >= beforeContentSize) {
            cursor.row = beforeContentSize
            return
        }
    }

    override fun toCursorBottom() {
        val cursorAnimation = editor.getController().style.cursorAnimation
        val cursor = editor.getContentProvider().getCursor()
        execCursorAnimationIfNeeded(cursorAnimation, editor) {
            toCursorBottomInternal()
        }
        editor.scrollToColumn(cursor.column)
    }

    private fun toCursorBottomInternal() {
        val contentProvider = editor.getContentProvider()
        val cursor = contentProvider.getCursor()
        val cursorColumn = cursor.column
        val cursorRow = cursor.row
        val columnCount = contentProvider.columnCount

        if (cursorColumn == columnCount) {
            return
        }

        if (cursorRow == 0 && cursorColumn < columnCount) {
            ++cursor.column
            cursor.row = 0
            return
        }

        ++cursor.column
        val afterContentSize = contentProvider.getColumnRowCount(cursor.column)
        if (cursorRow < afterContentSize) {
            return
        }

        if (cursorRow >= afterContentSize) {
            cursor.row = afterContentSize
            return
        }
    }

    private fun toCursorRightInternal() {
        val contentProvider = editor.getContentProvider()
        val cursor = contentProvider.getCursor()
        val cursorColumn = cursor.column
        val cursorRow = cursor.row
        val contentSize = contentProvider.getColumnRowCount(cursorColumn)

        if (cursorRow == contentSize && cursorColumn == contentProvider.columnCount) {
            return
        }

        if (cursorRow == contentSize && cursorColumn < contentProvider.columnCount) {
            ++cursor.column
            cursor.row = 0
            return
        }

        ++cursor.row
    }

    override fun toCursorHome() {
        val cursorAnimation = editor.getController().style.cursorAnimation
        val cursor = editor.getContentProvider().getCursor()
        execCursorAnimationIfNeeded(cursorAnimation, editor) {
            cursor.column = 1
            cursor.row = 0
        }
        editor.scrollToTop()
    }

    override fun toCursorEnd() {
        val contentProvider = editor.getContentProvider()
        val cursorAnimation = editor.getController().style.cursorAnimation
        val cursor = contentProvider.getCursor()
        execCursorAnimationIfNeeded(cursorAnimation, editor) {
            cursor.column = contentProvider.columnCount
            cursor.row = contentProvider.getColumnRowCount(contentProvider.columnCount)
        }
        editor.scrollToBottom()
    }

    override fun onVirtualKeyboardInput(event: KeyEvent) {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_SPACE && editor.getController().state.selection) {
            onSelectionTextReplace(" ")
            return
        }

        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            onCommit(" ")
            return
        }

        if (event.isPrintingKey && !editor.getController().state.selection) {
            onCommit(String(bytes = byteArrayOf(event.unicodeChar.toByte())))
            return
        }

        if (event.isPrintingKey && editor.getController().state.selection) {
            onSelectionTextReplace(String(bytes = byteArrayOf(event.unicodeChar.toByte())))
            return
        }

    }


}