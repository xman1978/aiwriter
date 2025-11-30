package com.thunisoft.intelligenteditor.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.aspose.cells.License;
import com.aspose.cells.Encoding;
import com.aspose.cells.HtmlSaveOptions;
import com.aspose.cells.SaveFormat;
import com.aspose.cells.Workbook;
import com.aspose.cells.WorksheetCollection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import org.apache.commons.io.FileUtils;

public class ExcelToHtmlUtil {

    private static final Logger logger = LoggerFactory.getLogger(ExcelToHtmlUtil.class);

    /**
     * 将 Excel 文件转换为 HTML 字符串（图片以 base64 嵌入）。
     * 兼容 .xls 和 .xlsx 文件（使用 Aspose.Cells）。
     *
     * @param file Excel 文件
     * @return HTML 内容（UTF-8），发生异常时返回空字符串
     */
    public static String extractTxtFromExcel(File file) {
        StringBuffer result = new StringBuffer();
        if (file == null || !file.exists()) {
            return result.toString();
        }

        try (InputStream is = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // 从输入流创建工作簿
            Workbook workbook = new Workbook(is);

            // 配置 HTML 保存选项
            HtmlSaveOptions options = new HtmlSaveOptions(SaveFormat.HTML);
            // 将图片嵌入为 Base64，使得 HTML 完全自包含
            options.setExportImagesAsBase64(false);
            // 导出所有工作表（如果只想导出当前激活表，可设 true）
            options.setExportActiveWorksheetOnly(false);
            // 保留表头
            options.setExportHeadings(false);
            // 保留表格
            options.setExportGridLines(false);
            // 不保留未使用的样式
            options.setExcludeUnusedStyles(true);
            // 不导出框架脚本和属性
            options.setExportFrameScriptsAndProperties(false);
            // 只导出打印区域
            options.setExportPrintAreaOnly(false);
            // 不导出评论
            options.setExportComments(false);
            // 不导出文档属性
            options.setExportDocumentProperties(false);
            // 不导出单元格 CSS 前缀
            options.setCellCssPrefix(null);
            // 不导出伪行数据
            options.setExportBogusRowData(false);
            // 设置编码
            options.setEncoding(Encoding.getUTF8());
            
            StringBuffer tableResult = new StringBuffer();
            WorksheetCollection worksheets = workbook.getWorksheets();
            for (int i = 0; i < worksheets.getCount(); i++) {
                workbook.getWorksheets().setActiveSheetIndex(i);
                options.setExportActiveWorksheetOnly(true);
                workbook.save(baos, options);
                tableResult.append(baos.toString(StandardCharsets.UTF_8.name()));
                baos.reset();
            }

            // 用 Jsoup 解析并提取 table，并清理元素属性
            Document doc = Jsoup.parse(tableResult.toString());
            Elements tables = doc.select("table");
            for (Element table : tables) {
                cleanElement(table);
                result.append(table.outerHtml());
            }
        } catch (Exception e) {
            logger.error("Excel 转 HTML 失败: {}", file.getAbsolutePath(), e);
        }

        return result.toString();
    }

    // 递归清理元素属性
    private static void cleanElement(Element element) {
        // 移除自身属性，如 style/class/border/width/height 等
        element.clearAttributes();

        // 递归清理所有子节点的属性
        for (Element child : element.children()) {
            cleanElement(child);
        }
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

        File f = new File("C:/Users/xman/Desktop/知识图谱/万相公文客户商机动态统计表（20250813）.xlsx");
        String html = extractTxtFromExcel(f);
        FileUtils.writeStringToFile(new File("C:/Users/xman/Desktop/1.html"), html, StandardCharsets.UTF_8);
    }
    */
}
