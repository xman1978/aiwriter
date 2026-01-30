package com.thunisoft.intelligenteditor.util;

import com.aspose.words.Document;
import com.aspose.words.LoadOptions;
import com.aspose.words.Field;
import com.aspose.words.NodeType;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.Paragraph;
import com.aspose.words.Table;
import com.aspose.words.Row;
import com.aspose.words.Cell;
import org.apache.commons.lang3.StringUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aspose.words.License;
import org.apache.commons.io.FileUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class WordToTxtExtractor {
    private static final Logger logger = LoggerFactory.getLogger(WordToTxtExtractor.class);

    public static String extractTxtFromDoc(File file) {
        String result = "";

        if (!file.exists()) {
            logger.error("文件不存在: {}", file.getAbsolutePath());
            return result;
        }

        try (InputStream is = new FileInputStream(file)) {

            // 加载 Word
            LoadOptions loadOptions = new LoadOptions();
            loadOptions.getLanguagePreferences().setDefaultEditingLanguage(2052);
            Document doc = new Document(is, loadOptions);
            // xman:2026-01-30 更新列表标签和表格布局
            doc.updateListLabels();
            doc.updateTableLayout();

            // 修订处理
            doc.getRevisions().acceptAll();

            // 删除域（如 Hyperlink, PAGE 等）
            for (Field field : doc.getRange().getFields()) {
                field.remove();
            }

            StringBuilder sb = new StringBuilder();

            @SuppressWarnings("unchecked")
            NodeCollection<Node> nodes = doc.getChildNodes(NodeType.ANY, true);
            for (Node node : nodes) {
                if (node.getNodeType() == NodeType.TABLE) {
                    sb.append(convertTableToMarkdown((Table) node)).append("\n");
                } else if (node.getNodeType() == NodeType.PARAGRAPH) {
                    Paragraph paragraph = (Paragraph) node;
 
                    if (paragraph.isInCell()) {
                        continue;
                    }

                    if (paragraph.isListItem()) {
                        sb.append(paragraph.getListLabel().getLabelString() + " ");
                    }
                    sb.append(paragraph.getText()).append("\n");                    
                }
            }

            // 清理结果
            result = cleanOutput(sb.toString());
            return result;

        } catch (Exception e) {
            logger.error("Word 转 TXT 失败: {}",  file.getAbsolutePath(), e);
        }

        return result;
    }

    // ===============================
    //     Markdown 表格智能对齐版
    // ===============================
    private static String convertTableToMarkdown(Table table) {
        List<List<String>> rows = new ArrayList<>();

        for (Row row : table.getRows()) {
            List<String> cols = new ArrayList<>();

            for (Cell cell : row.getCells()) {
                StringBuilder cellText = new StringBuilder();
                for (Paragraph p : cell.getParagraphs()) {
                    String t = p.getText()
                            .replace("\r", "")
                            .replace("\n", " ")
                            .replace("\f", "")
                            .trim();
                    if (!t.isEmpty()) {
                        if (cellText.length() > 0) cellText.append(" ");
                        cellText.append(t);
                    }
                }
                cols.add(cellText.toString());
            }
            rows.add(cols);
        }

        if (rows.isEmpty()) return "";

        // ① 自动合并重复表头
        rows = mergeDuplicateHeaderRows(rows);

        // ② 对齐列宽 - 先找到最大列数
        int colCount = 0;
        for (List<String> row : rows) {
            colCount = Math.max(colCount, row.size());
        }
        
        // 标准化所有行，确保列数一致（不足的用空字符串填充）
        for (List<String> row : rows) {
            while (row.size() < colCount) {
                row.add("");
            }
        }
        
        int[] colWidths = new int[colCount];
        for (List<String> row : rows) {
            for (int i = 0; i < colCount; i++) {
                colWidths[i] = Math.max(colWidths[i], row.get(i).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        // 添加表格开始标签
        sb.append("<table>\n");

        // Header
        List<String> header = rows.get(0);
        sb.append("|");
        for (int i = 0; i < colCount; i++) {
            sb.append(" ")
                    .append(StringUtils.rightPad(header.get(i), colWidths[i]))
                    .append(" |");
        }
        sb.append("\n");

        // Separator
        sb.append("|");
        for (int i = 0; i < colCount; i++) {
            sb.append(" ").append(StringUtils.repeat("-", colWidths[i])).append(" |");
        }
        sb.append("\n");

        // Body
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            sb.append("|");
            for (int i = 0; i < colCount; i++) {
                sb.append(" ").append(StringUtils.rightPad(row.get(i), colWidths[i])).append(" |");
            }
            sb.append("\n");
        }

        sb.append("</table>\n");

        return sb.toString();
    }

    // ============== 工具函数 ==============

    private static List<List<String>> mergeDuplicateHeaderRows(List<List<String>> rows) {
        if (rows.size() < 2) return rows;

        List<String> first = normalizeRow(rows.get(0));
        int removeCount = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> current = normalizeRow(rows.get(i));
            if (current.equals(first)) {
                removeCount++;
            } else {
                break;
            }
        }

        if (removeCount > 0) {
            return rows.subList(0, rows.size() - removeCount);
        }

        return rows;
    }

    private static List<String> normalizeRow(List<String> row) {
        List<String> normalized = new ArrayList<>();
        for (String col : row) {
            normalized.add(col.trim().replace("　", " ").replaceAll("\\s+", " "));
        }
        return normalized;
    }

    private static String cleanOutput(String text) {
        if (StringUtils.isBlank(text)) 
            return "";

        return text
                .replace("\r", "\n")
                .replace("\f", "")
                .replace("\013", "")
                .replaceAll("\u0007+", "\n")
                .replaceAll("[\u0017\u0019\u001A]", "")
                .replaceAll("^\\n+", "")
                .trim();
    }

    // 本地测试用
    /*
    public static void main(String[] args) throws Exception {
        StringBuilder licenseBody = new StringBuilder().append("<License>")
        .append("<Data>")
        .append("<LicensedTo>BEIJING THUNISOFT INFORMATION TECHNOLOGY CORPORATION LIMITED</LicensedTo>")
        .append("<EmailTo>zhyue@thunisoft.com</EmailTo>")
        .append("<LicenseType>Developer OEM</LicenseType>")
        .append("<LicenseNote>Limited to 1 developer, unlimited physical locations</LicenseNote>")
        .append("<OrderID>191120204421</OrderID>")
        .append("<UserID>135030163</UserID>")
        .append("<OEM>This is a redistributable license</OEM>")
        .append("<Products>")
        .append("<Product>Aspose.Total for Java</Product>")
        .append("</Products>")
        .append("<EditionType>Enterprise</EditionType>")
        .append("<SerialNumber>514bcc3d-a530-4d77-bdfa-0312b45ad57e</SerialNumber>")
        .append("<SubscriptionExpiry>20201120</SubscriptionExpiry>")
        .append("<LicenseVersion>3.0</LicenseVersion>")
        .append("<LicenseInstructions>https://purchase.aspose.com/policies/use-license</LicenseInstructions>")
        .append("</Data>")
        .append("<Signature>X3Z7IcfQV0QZw9A3dVhCrvJ9I0siOPJnt33nzhYoYgg755onmMPB48PK5sMOnaEwexkdqEZ+z9ODUasCUy1aQMUk54BTprbG3iwixFS5uvRZsFw2jTmVqUcPM7dpa5Miw6c0NTu8MhwshvvjQxATgP4qj5LxWFN3UkSL6zgmwvs=</Signature>")
        .append("</License>");
        License license = new License();
        try {
            license.setLicense(new ByteArrayInputStream(licenseBody.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            e.printStackTrace();
        }

        File f = new File("C:/Users/xman/Desktop/知识图谱/万相公文项目建设立材料（通用模版）.docx");
        String text = extractTxtFromDoc(f);
        FileUtils.writeStringToFile(new File("C:/Users/xman/Desktop/1.txt"), text, StandardCharsets.UTF_8);
    }
     */
}
