package com.intellij.idea.plugin.hybris.impex.tableFormatting;

import com.intellij.idea.plugin.hybris.impex.psi.ImpexAnyHeaderMode;
import com.intellij.idea.plugin.hybris.impex.psi.ImpexFullHeaderParameter;
import com.intellij.idea.plugin.hybris.impex.psi.ImpexHeaderTypeName;
import com.intellij.idea.plugin.hybris.impex.psi.ImpexValue;
import com.intellij.idea.plugin.hybris.impex.psi.ImpexValueLine;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.idea.plugin.hybris.impex.utils.CommonPsiUtils.getNextSiblingOfAnyType;
import static com.intellij.idea.plugin.hybris.impex.utils.ImpexPsiUtils.isHeaderLine;
import static com.intellij.idea.plugin.hybris.impex.utils.ImpexPsiUtils.isImpexValueGroup;
import static com.intellij.idea.plugin.hybris.impex.utils.ImpexPsiUtils.isImpexValueLine;
import static com.intellij.idea.plugin.hybris.impex.utils.ImpexPsiUtils.isParameterSeparator;
import static com.intellij.idea.plugin.hybris.impex.utils.ImpexPsiUtils.isWhiteSpace;
import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfAnyType;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;
import static com.intellij.psi.util.PsiTreeUtil.getChildrenOfType;
import static com.intellij.psi.util.PsiTreeUtil.getNextSiblingOfType;

/**
 * @author Aleksandr Nosov <nosovae.dev@gmail.com>
 */
public class ImpexTableFormatter {

    private ImpexTableFormatter() {
    }

    public static ImpexTable format(final Pair<PsiElement, PsiElement> table) {
        final ImpexTableFormatter tableFormatter = new ImpexTableFormatter();

        final int[] maxColumnWidth = tableFormatter.calculateMaxWidth(table, table.first);

        final StringBuilder sb = tableFormatter.createNewTable(table, maxColumnWidth);

        final int startOffset = table.first.getTextRange().getStartOffset() == 0
            ? table.first.getTextRange().getStartOffset()
            : table.first.getTextRange().getStartOffset();
        final int endOffset = table.second.getTextRange().getEndOffset();

        return new ImpexTable(startOffset, endOffset, sb.toString());
    }


    @NotNull
    private StringBuilder createNewTable(
        final Pair<PsiElement, PsiElement> table,
        final int[] maxColumnWidth
    ) {
        final StringBuilder sb = new StringBuilder();
        PsiElement currentValueLine = table.first;
        boolean keepProcess = true;
        while (keepProcess) {
            if (currentValueLine == null) {
                break;
            }
            if (isHeaderLine(currentValueLine)) {
                writeHeader(table.first, maxColumnWidth, sb);
            } else if (isImpexValueLine(currentValueLine)) {
                writeValueLine(maxColumnWidth, sb, currentValueLine);
            } else {
                sb.append(currentValueLine.getText().replaceAll(" ", ""));
            }

            if (table.second.equals(currentValueLine)) {
                keepProcess = false;
                continue;
            }
            currentValueLine = getNextSiblingOfAnyType(currentValueLine, PsiElement.class);
        }
        return sb;
    }

    private void writeValueLine(final int[] maxColumnWidth, final StringBuilder sb, final PsiElement currentValueLine) {
        if (isImpexValueGroup(currentValueLine.getFirstChild())) {
            sb.append(StringUtils.rightPad("", maxColumnWidth[0] + 1));
        } else {
            final String text = StringUtils.rightPad(
                currentValueLine.getFirstChild().getText().trim(),
                maxColumnWidth[0]
            );
            sb.append(text);
        }
        final PsiElement[] children = currentValueLine.getChildren();

        int i = 1;
        for (final PsiElement element : children) {
            if (isFirstFieldValueIsEmpty(element)) {
                sb.append(';').append(' ').append(StringUtils.rightPad("", maxColumnWidth[i]));
            } else {
                sb
                    .append(';')
                    .append(' ')
                    .append(StringUtils.rightPad(element.getLastChild().getText().trim(), maxColumnWidth[i]));
            }
            i++;
        }
    }

    private static boolean isFirstFieldValueIsEmpty(final PsiElement element) {
        final PsiElement[] childrenOfType = getChildrenOfType(element, PsiElement.class);
        return childrenOfType != null && childrenOfType.length == 1;
    }

    private void writeHeader(
        final PsiElement header,
        final int[] maxColumnWidth,
        final StringBuilder sb
    ) {
        final Collection<PsiElement> columns = findChildrenOfAnyType(
            header,
            ImpexAnyHeaderMode.class,
            ImpexFullHeaderParameter.class,
            ImpexHeaderTypeName.class,
            PsiElement.class
        );

        int i = 1;
        for (PsiElement column : columns) {
            if (isWhiteSpace(column)) {
                continue;
            }

            if (isParameterSeparator(column)) {
                sb.append(column.getText()).append(' ');

            } else if (column instanceof ImpexAnyHeaderMode
                       || column instanceof ImpexHeaderTypeName
                       || column instanceof ImpexFullHeaderParameter) {

                if (column instanceof ImpexAnyHeaderMode
                    || column instanceof ImpexHeaderTypeName) {

                    sb.append(column.getText());
                    if (column instanceof ImpexAnyHeaderMode) {
                        sb.append(' ');
                    }
                } else {
                    final int width = maxColumnWidth[i];
                    if (i == maxColumnWidth.length - 1) {
                        sb.append(column.getText());
                    } else {
                        sb.append(StringUtils.rightPad(column.getText().trim(), width));
                    }
                    i++;
                }
            }
        }
    }

    private int[] calculateMaxWidth(final Pair<PsiElement, PsiElement> table, @NotNull final PsiElement header) {
        final Collection<PsiElement> columns = findChildrenOfAnyType(
            header,
            ImpexAnyHeaderMode.class,
            ImpexFullHeaderParameter.class,
            ImpexHeaderTypeName.class,
            PsiWhiteSpace.class
        );

        final int[] maxColumnWidth = new int[header.getChildren().length - 1];

        int k = 0;
        for (PsiElement column : columns) {
            if (column.getPrevSibling() != null && isParameterSeparator(column.getPrevSibling())) {
                k++;
            }
            if (!(column instanceof PsiWhiteSpace)) {
                maxColumnWidth[k] += column.getText().replaceAll(";", "").length();
            }
        }


        PsiElement currentValueLine = getNextSiblingOfType(header, ImpexValueLine.class);
        boolean keepProcessing = true;
        while (keepProcessing) {
            if (currentValueLine == null) {
                break;
            }
            if (!isImpexValueGroup(currentValueLine.getFirstChild())) {
                final int textLength = currentValueLine.getFirstChild().getText().replace(";", "").length();
                maxColumnWidth[0] = Math.max(textLength, maxColumnWidth[0]);
            }
            final PsiElement[] children = currentValueLine.getChildren();

            int i = 1;
            for (final PsiElement element : children) {
                if (!isFirstFieldValueIsEmpty(element)) {
                    final int textLength = getChildOfType(element, ImpexValue.class).getTextLength();
                    maxColumnWidth[i] = Math.max(textLength, maxColumnWidth[i]);
                }
                i++;
            }


            if (table.second.equals(currentValueLine)) {
                keepProcessing = false;
                continue;
            }
            currentValueLine = getNextSiblingOfType(currentValueLine, ImpexValueLine.class);
        }
        return maxColumnWidth;
    }
}
