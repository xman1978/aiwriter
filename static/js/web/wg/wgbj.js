/*********************************************
 * 文稿撰写 JS
 *
 * @author fang_
 * @date 2024-03-13
 *********************************************/
let INTERVAL = 5000;
// let INTERVAL_MANUAL = 1000;
// let TIME_ID = ""
let IS_AUTO_SAVING = false;

let lastMd5Sum = ""
// 最大文件名称长度
let max_file_name_length = 25;

// 最大缩略内容
let max_abbr_content_length = 200;

// 是否使用深度思考
let useDeepThinking = undefined;

let modeType;
let JdType = 'start';

/**
 * 页面数据
 * params：url参数
 *  iscallback：是否新版插件，公文编校里打开时会传的
 *  embed：是否嵌入到公文编校里
 *  fontZoom：页面缩放比例，公文编校里打开时会传的
 *  tenantCode: 单位id，用于统计
 * user：用户相关信息
 * dataDir：保存用户信息的文件路径（旧版插件）
 * cookieContent：从dataDir读取的用户信息（旧版插件）
 * setting：设置信息
 *     zdssjg  折叠搜索结果
 *     xssxw   是否展开上下文
 *     xssxwsl 展开上下文数量
 */
var _PAGEDATA = {
    params: {
        iscallback: false,
        embed: false,
        isWeb: true,
        fontZoom: 1,
        version: getQueryVariable('version'),
        tenantCode: getQueryVariable('tenantCode'),
        uCp: getQueryVariable('uCp'),
        iQt: getQueryVariable('iQt'),
        vKt: getQueryVariable('vKt'),
        fromType: getQueryVariable('fromType'),
        ksp_userId: getQueryVariable('ksp_userId'),
        ksp_userName: getQueryVariable('ksp_userName'),
        ksp_courtCode: getQueryVariable('ksp_courtCode'),
        ksp_courtName: getQueryVariable('ksp_courtName')
    },
    user: {
        userId: '',
        deptId: '',
        corpId: '',
        tenantCode: ''
    },
    setting: {
        zdssjg: false,
        xssxw: true,
        xssxwsl: 2147483647
    }
};

// 是否是web版本
isWeb = true;

// 公文智写部分：当前显示iframe
_iframeId_gwzx = "atyIframeQc";

// 公文资料库部分：当前显示iframe
_iframeId_gwzlk = "ifmZsjs";

// 用于解析markdown语法时，标识当前插入的行内容是否是列表类型
var _liststart = null;
// 判断是否是列表的正则
var _listpattern = /^-[ \s]/;
// 判断是否是序号的正则
var _seqpattern = /^(\d[.])[ \s]([^\n]*)/;

var _uiconfig = {gwzk: true, ai: true};// 开关

// 资源加载完成事件
window.addEventListener('load', function () {
    // 延迟加载公文资料库和公文智写
    Artery.request({
        url: 'web/wg/wgbj/getShowStatus',
        method: "GET",
        success: function (data) {
            var userInfo = btoa(encodeURI(JSON.stringify({"userId":_PAGEDATA.user.username,"corpId":_PAGEDATA.user.corpId,"deptId":_PAGEDATA.user.deptId,"tenantCode": _PAGEDATA.user.tenantCode})));
            Artery.get('atyIframeXy').showLink(data+'?userInfo=' + userInfo + '&isWeb=' + _PAGEDATA.params.isWeb + '&embed=' + _PAGEDATA.params.embed);
        },
        error: function (err, msg) {
        }
    })
    setTimeout(function () {
        loadOtherIframePage();
    }, 1000)
});
/**
 * @param rc
 */
function onLoadClient(rc) {


    // if (_PAGEDATA.params.fromType != 'undefined' && _PAGEDATA.params.fromType == 'xiaozhi') {
        // document.title = "小至公文-公文AI写作平台";

        /*if(_PAGEDATA.params.fromType!='undefined' && _PAGEDATA.params.fromType=='xiaozhi'){
            document.title = "小至公文-公文AI写作平台";
            document.getElementById("atyDropDownBtnUser").style.display="none";
            document.getElementById("atyTextLhick").style.display="";
            document.getElementById("btnNavZwbw").style.display="none";         //隐藏写作助理
        }else if(_PAGEDATA.params.ksp_userId!='undefined'){
            // document.title = "公文生成-公文AI写作平台";
            document.getElementById("atyDropDownBtnUser").style.display="";
            document.getElementById("atyTextLhick").style.display="none";
        }else{
            // document.title = "万象公文-公文AI写作平台";
            document.getElementById("atyDropDownBtnUser").style.display="";
            document.getElementById("atyTextLhick").style.display="none";
        }*/

        initTinyMce();
        // 注册页面分隔条拖动事件
        initSplitEvent();
        // 设置页面布局状态
        updateRegionState();
        // 解析用户信息
        parseUserInfo();
        getDeepSeekInfo();
        // 检查开关
        checkUIConfig();
        // 检查其他的配置
        checkOtherConfig();
        // 加载默认页面
        loadDefaultIframePage();
    // }
}

function initTinyMce() {
    var gx = "gwgx";
    if (Artery.customParams && Artery.customParams.gxrsEnabled) {
        if(Artery.customParams.gxrsEnabled === 'true'){
            gx = "gxrs";
        }
    }
    if (typeof tinymce !== 'undefined') {
        isLoadOldTinymce ? onLoadClient5(gx) : onLoadClient6(gx);
    } else {
        setTimeout(initTinyMce, 50);
    }
}

function onLoadClient5(gx) {
    tinymce.init({
        // 整体配置
        selector: '#textareaDoc',
        language: "zh_CN",
        height: '100%',
        skin: 'oxide',
        icons: 'gw',
        content_css: ['default', ARTERY_GLOBAL.CONTEXT_PATH + '/css/web/wg/content.css'],
        // 菜单来
        menubar: false,
        promotion: false,
        // 插件
        plugins: 'gwbj ' + gx + ' wordcount quickbars lists table advlist fullscreen pagebreak searchreplace image',
        // 工具栏
        // wordcount styles quicktable image pagebreak fontsizeinput paste pastetext visualblocks visualchars
        toolbar: 'gwbj openDocButton searchreplace undo redo removeformat formatselect fontselect fontsizeselect bold italic underline strikethrough forecolor lineheight alignleft aligncenter alignright alignjustify indent outdent table fullscreen downloadDocButton tts textformat clear',
        toolbar_mode: "sliding",
        font_formats: "宋体=simsun,serif; 仿宋体=FangSong,serif; 黑体=SimHei,sans-serif; 楷体=KaiTi; 方正小标宋=FZXBSJW-GB1-0, 方正小标宋, sans-serif; Andale Mono=andale mono,times; Arial=arial,helvetica,sans-serif; Arial Black=arial black,avant garde; Book Antiqua=book antiqua,palatino; Comic Sans MS=comic sans ms,sans-serif; Courier New=courier new,courier; Georgia=georgia,palatino; Helvetica=helvetica; Impact=impact,chicago; Symbol=symbol; Tahoma=tahoma,arial,helvetica,sans-serif; Terminal=terminal,monaco; Times New Roman=times new roman,times; Trebuchet MS=trebuchet ms,geneva; Verdana=verdana,geneva; Webdings=webdings; Wingdings=wingdings,zapf dingbats",
        fontsize_formats: '初号=44pt 小初=36pt 一号=26pt 小一=24pt 二号=22pt 小二=18pt 三号=16pt 小三=15pt 四号=14pt 小四=13pt 五号=10.5pt 小五=9pt 六号=7.5pt 小六=6.5pt 七号=5.5pt 八号=5pt 5pt 5.5pt 6pt 6.5pt 7pt 7.5pt 8pt 9pt 10pt 11pt 12pt 14pt 16pt 18pt 20pt 22pt 24pt 26pt 28pt 36pt 48pt 56pt 72pt',
        allow_html_in_named_anchor: true,
        // 快速工具栏
        quickbars_insert_toolbar: false,
        quickbars_selection_toolbar: false,
        quickbars_image_toolbar: false,
        // 右键菜单
        contextmenu: gx,
        // 状态栏
        statusbar: true,
        elementpath: false,
        resize: false,
        branding: false,
        // 初始化
        setup: (editor) => {
            tinymce_setup(editor);
        },
        init_instance_callback: async function (editor) {
            tinymce_init_callback(editor);
        }
    });
    // 将校对选项写入到cookie中
    writeCookie(true);
}

function onLoadClient6(gx) {
    // 浏览器检查
    if (tinymce.Env.browser.isChromium() && tinymce.Env.browser.version.major < 83) {
        Artery.modal.confirm({
            title: '提示',
            content: `浏览器内核主版本为：${tinymce.Env.browser.version.major}。<br>浏览器版本过低，是否下载84版本的Chrome浏览器？`,
            okText: '下载',
            maskClosable: true,
            onOk: function () {
                window.location.href = ARTERY_GLOBAL.CONTEXT_PATH + "/file/84.0.4147.105_chrome32_stable_windows_installer.exe";
            }
        });
    }
    tinymce.init({
        // 整体配置
        selector: '#textareaDoc',
        language: "zh-Hans", // "zh-CN"
        height: '100%',
        skin: 'oxide',
        icons: 'gw',
        content_css: ['default', ARTERY_GLOBAL.CONTEXT_PATH + '/css/web/wg/content.css'],
        // 菜单来
        menubar: false,
        promotion: false,
        // 插件
        plugins: 'gwbj ' + gx + ' wordcount quickbars lists table advlist fullscreen pagebreak searchreplace image',
        // 工具栏
        // wordcount styles quicktable image pagebreak fontsizeinput paste pastetext visualblocks visualchars
        toolbar: 'openDocButton searchreplace undo redo removeformat blocks fontfamily fontsize bold italic underline strikethrough forecolor lineheight alignleft aligncenter alignright alignjustify indent outdent table fullscreen downloadDocButton tts textformat clear',
        toolbar_mode: "sliding",
        font_family_formats: "宋体=simsun,serif; 仿宋体=FangSong,serif; 黑体=SimHei,sans-serif; 楷体=KaiTi; 方正小标宋=FZXBSJW-GB1-0, 方正小标宋, sans-serif; Andale Mono=andale mono,times; Arial=arial,helvetica,sans-serif; Arial Black=arial black,avant garde; Book Antiqua=book antiqua,palatino; Comic Sans MS=comic sans ms,sans-serif; Courier New=courier new,courier; Georgia=georgia,palatino; Helvetica=helvetica; Impact=impact,chicago; Symbol=symbol; Tahoma=tahoma,arial,helvetica,sans-serif; Terminal=terminal,monaco; Times New Roman=times new roman,times; Trebuchet MS=trebuchet ms,geneva; Verdana=verdana,geneva; Webdings=webdings; Wingdings=wingdings,zapf dingbats",
        font_size_formats: '初号=44pt 小初=36pt 一号=26pt 小一=24pt 二号=22pt 小二=18pt 三号=16pt 小三=15pt 四号=14pt 小四=13pt 五号=10.5pt 小五=9pt 六号=7.5pt 小六=6.5pt 七号=5.5pt 八号=5pt 5pt 5.5pt 6pt 6.5pt 7pt 7.5pt 8pt 9pt 10pt 11pt 12pt 14pt 16pt 18pt 20pt 22pt 24pt 26pt 28pt 36pt 48pt 56pt 72pt',
        allow_html_in_named_anchor: true,
        // 快速工具栏
        quickbars_insert_toolbar: false,
        quickbars_selection_toolbar: false,
        quickbars_image_toolbar: false,
        // 右键菜单
        contextmenu: gx,
        // 状态栏
        statusbar: true,
        elementpath: false,
        resize: false,
        branding: false,
        // 初始化
        setup: (editor) => {
            tinymce_setup(editor);
        },
        init_instance_callback: async function (editor) {
            tinymce_init_callback(editor);
        }
    });
    // 将校对选项写入到cookie中
    writeCookie(true);
}

function tinymce_setup(editor) {
    // 工具栏
    editor.ui.registry.addButton('openDocButton', {
        icon: 'shangchuan', // new-document upload
        tooltip: '打开文档',
        onAction: () => {
            var filetype = '.doc, .docx, .wps';
            var upurl = ARTERY_GLOBAL.CONTEXT_PATH + '/web/wg/wgbj/doctohtml';
            var input = document.getElementById('wgbj-openDocButton');
            if(!input) {
                input = document.createElement('input');
                input.setAttribute('type', 'file');
                input.setAttribute('accept', filetype);
                input.setAttribute('id', 'wgbj-openDocButton');
                // 73版chrome上发现如果和自动保存同时触发会导致onchange不发生，不清楚原因，append到body就好了
                document.body.append(input);
                input.onchange = function () {
                    document.body.removeChild(input);
                    var file = this.files[0];

                    var xhr, formData;
                    console.log(file.name);
                    xhr = new XMLHttpRequest();
                    xhr.withCredentials = false;
                    xhr.open('POST', upurl);
                    xhr.onload = function () {
                        var json;
                        if (xhr.status != 200) {
                            console.log('HTTP Error: ' + xhr.status);
                            return;
                        }
                        json = JSON.parse(xhr.responseText);
                        if (json.success === true) {
                            setEditorContent(json.data);
                        } else {
                            console.log(json.message);
                        }
                    };
                    formData = new FormData();
                    formData.append('file', file, file.name);
                    xhr.send(formData);
                };
            }
            input.click();
        }
    });
    editor.ui.registry.addButton('downloadDocButton', {
        icon: 'xiazai', // export export-word
        tooltip: '下载文档',
        onAction: () => {
            if('safemode' in Artery.params && Artery.params.safemode === 'true') {
                /*Artery.download('web/wg/wgbj/downloaddoc2', {
                    data: {
                        content: getEditorContentHtml(),
                        fileName: Artery.get('textHeaderTitle').getValue()
                    },
                    mask: false,
                })*/
                Artery.request({
                    url: "web/wg/wgbj/saveOnSafe",
                    type: 'POST',
                    contentType: 'application/json;charset=UTF-8',
                    data: {
                        content: getEditorContentHtml(),
                        fileName: Artery.get('textHeaderTitle').getValue()
                    },
                    success: function (response) {
                        console.log('下载成功');
                        let downloadUrl = ARTERY_GLOBAL.CONTEXT_PATH + '/web/wg/wgbj/downloaddoc2?id=' + response + '&fileName=' + Artery.get('textHeaderTitle').getValue();
                        window.open(downloadUrl, '_self');
                    },
                    error: function (err) {
                        console.log('下载失败');
                    }
                })
            }else {
                // 没自动保存就点下载，会有问题
                saveDoc(function () {
                    let downloadUrl = ARTERY_GLOBAL.CONTEXT_PATH + '/web/wg/wgbj/downloaddoc?id=' + Artery.params.id;
                    window.open(downloadUrl, '_self');
                })
            }
        }
    });
    editor.ui.registry.addButton('textformat', {
        icon: 'ai-prompt',
        tooltip: '排版',
        text: '排版',
        onAction: () => {
            format();
        }
    });
    editor.ui.registry.addButton('clear', {
        icon: 'remove',
        tooltip: '清空内容',
        text: '清空内容',
        onAction: () => {
            clearContent();
        }
    });
    addTTSButton(editor);
    /*粘贴过来html容易出问题editor.on('paste', (event) => {
        var str = event.clipboardData.getData('text/html').replace(/\r\n/g, '\n');
        if(str.length === 0) {
            str = event.clipboardData.getData('text/plain').replace(/\r\n/g, '\n');
        }else {
            str = str.replace('<html>\n', '').replace('\n</html>', '');
            str = str.replace('<body>\n', '').replace('\n</body>', '');
        }
        insertToEditor(str, false, false, true);
        event.preventDefault();
        // insertToEditor(event.clipboardData.getData('text/plain').replace(/\r\n/g, '\n'), false, false, true);
        // event.preventDefault();
    });*/
    editor.on('init', (event) => {
        var wordCountButton = document.querySelector("button.tox-statusbar__wordcount");
        if(wordCountButton) {
            wordCountButton.click();
        }
    });
}

function tinymce_init_callback(editor) {
    // let element = document.createElement("div");
    // $(element).attr("id", "autoSaveTips").css("margin", "0 10px").css("display", "none");
    // $(".tox-statusbar").append(element);
    // 增加AIGC提示
    addAIGCTipS()
    // 获取前一个文本
    setTimeout(() => {
        preloadEditorText()
    }, 100);
    autoSaveAtInterval()
    editor.on('copy', (event) => {
        let content = editor.selection.getContent({format: 'text'});
        if (content) {
            content = content.replace(/\n\n/g, "\n");
            event.clipboardData.setData("text/plain", content);
            event.preventDefault();
        }
    });
}

function clearContent() {
    var editor = tinymce.get('textareaDoc');
    editor.setContent("");
}

const _format_style = {
    '文书标题':'text-align: center;font-weight: bold;font-size: 22pt;line-height: 2.5;font-family: 方正小标宋简体;',
    '主送机关':'font-size: 16pt;line-height: 2;font-family: FangSong, serif;',
    '正文':'font-size: 16pt;line-height: 2;font-family: FangSong, serif;text-indent: 2em;',
    '一级标题':'font-family: SimHei, sans-serif;font-size: 16pt;text-indent: 2em;line-height: 2;',
    '二级标题':'font-family: KaiTi;font-size: 16pt;text-indent: 2em;line-height: 2;',
    '三级标题':'font-family: FangSong, serif;font-size: 16pt;text-indent: 2em;',
    '四级标题':'font-family: FangSong, serif;font-size: 16pt;text-indent: 2em;',
    '发文机关署名':'text-align: right;font-size: 16pt;line-height: 2;font-family: FangSong, serif;margin-right: 2em;',
    '成文日期':'text-align: right;font-size: 16pt;line-height: 2;font-family: FangSong, serif;margin-right: 2em;',
    '附件说明':'font-family: FangSong, serif;font-size: 16pt;',
    '附件':'font-family: SimHei, sans-serif;font-size: 16pt;',
    '附注':'font-family: SimHei, sans-serif;font-size: 16pt;',
    '附件标题':'text-align: center;font-weight: bold;font-size: 22pt;line-height: 2.5;font-family: FZXBSJW-GB1-0, 方正小标宋, sans-serif;',
    '附件内容':'font-size: 16pt;line-height: 2;font-family: FangSong, serif;text-indent: 2em;',
    '附件一级标题':'font-family: SimHei, sans-serif;font-size: 16pt;text-indent: 2em;',
    '附件二级标题':'font-family: KaiTi;font-size: 16pt;text-indent: 2em;',
    '附件三级标题':'font-family: FangSong, serif;font-size: 16pt;text-indent: 2em;',
    '附件四级标题':'font-family: FangSong, serif;font-size: 16pt;text-indent: 2em;',
    'AI免责声明':'font-family: STSongti-SC, SimSun, serif; font-weight: bold; font-size: 18pt; color: #7D7D7D; line-height: 24.75pt; text-align: left; font-style: normal;',
};
function format(check) {
    // 检查是否格式化
    if(check) {
        var editor = tinymce.get('textareaDoc');
        var selectstr = editor.selection.getSel().toString().replace(/[\n]{2,}/g, '\n');
        var editorstr = editor.getContent({format: 'text'}).replace(/[\n]{2,}/g, '\n');
        if(selectstr !== editorstr) {
            return;
        }
    }
    Artery.mask(
        '正在格式化...', // 提示信息
        0, // 延迟加载遮罩时间，必填
        {
            showIcon: true, // 是否显示loading图标
            styleType: 'small' // 遮罩类型
        })

    var editorContent = getEditorContents();
    var content = editorContent[0];
    var images = editorContent[1];
    var tables = editorContent[2];

    Artery.request({
        url: "web/wg/wgbj/formatinfo",
        type: 'POST',
        data: {
            content: content
        },
        success: function (formatinfo) {
            Artery.unmask();
            var formatinfoObject = {};
            var formatinfoArray = formatinfo.split('\r\n');
            for(var i = 0; i < formatinfoArray.length; i++) {
                var name = formatinfoArray[i].substring(0, formatinfoArray[i].indexOf(':'));
                var parainfo = formatinfoArray[i].substring(formatinfoArray[i].indexOf(':')+1).split(',');
                var para = parseInt(parainfo[0]);
                var repeat = parseInt(parainfo[1]);
                for(var j = 0; j < repeat; j++) {
                    /**
                     * 特殊情况：
                     * 第一种：
                     * 二级标题:11,2,
                     * 正文:12,1,xxx
                     * 第二种：
                     * 二级标题:13,1,xxx
                     * 正文:13,1,xxx
                     */
                    if((para+j) in formatinfoObject) {
                        formatinfoObject[para+j].other = {
                            'style': name in _format_style ? _format_style[name] : '',
                            'content': parainfo.length >= 3 ? parainfo[2] : ''
                        };
                    }else {
                        formatinfoObject[para+j] = {
                            'style': name in _format_style ? _format_style[name] : '',
                            'content': parainfo.length >= 3 ? parainfo[2] : ''
                        };
                    }
                }
            }
            var editor = tinymce.get('textareaDoc');
            editor.setContent("");
            var strs = content.split('\n');
            for(var i = 0; i < strs.length; i++) {
                var tempStr = '';
                if(strs[i] === '[WXGW-IMAGE]') {
                    tempStr = '<p>' + images.splice(0, 1)[0] + '</p>';
                }else if(strs[i] === '[WXGW-TABLE]') {
                    tempStr = tables.splice(0, 1)[0] + '<p></p>';
                }else {
                    if(strs[i] === '以上内容为AI生成，仅供参考使用' && i === strs.length - 1) {
                        tempStr = '<p></p><p style="' + _format_style['AI免责声明'] + '">' + strs[i] + '</p>';
                    }else if((i+1) in formatinfoObject) {
                        var index = formatinfoObject[i+1].style.indexOf('font-size');
                        var fontSize = formatinfoObject[i+1].style.substring(index, index + formatinfoObject[i+1].style.substring(index).indexOf('pt'))
                        tempStr = '<p style="' + fontSize + 'pt;text-indent: 2em;">';
                        if('other' in formatinfoObject[i+1]) {// 段落内容是标题+正文形式，默认other里面是正文部分
                            if(formatinfoObject[i+1].content.length === 0) {// 上诉特殊情况的第一种
                                var index = strs[i].indexOf(formatinfoObject[i+1].other.content);
                                tempStr = tempStr + '<span style="' + formatinfoObject[i+1].style + '">' + strs[i].substring(0, index) + '</span>';
                                tempStr = tempStr + '<span style="' + formatinfoObject[i+1].other.style + '">' + formatinfoObject[i+1].other.content + '</span>';
                            }else {// 上诉特殊情况的第二种
                                tempStr = tempStr + '<span style="' + formatinfoObject[i+1].style + '">' + formatinfoObject[i+1].content + '</span>';
                                tempStr = tempStr + '<span style="' + formatinfoObject[i+1].other.style + '">' + formatinfoObject[i+1].other.content + '</span>';
                            }
                            tempStr = tempStr + '</p>';
                        }else {
                            tempStr = '<p style="' + formatinfoObject[i+1].style + '">' + strs[i] + '</p>';
                        }
                    }else {
                        tempStr = '<p>' + strs[i] + '</p>';
                    }
                    // 判断是否有图片
                    while(tempStr.indexOf('[WXGW-IMAGE]') !== -1) {
                        tempStr = tempStr.replace('[WXGW-IMAGE]', images.splice(0, 1)[0]);
                    }
                    // 判断是否有表格
                    while(tempStr.indexOf('[WXGW-TABLE]') !== -1) {
                        tempStr = tempStr.replace('[WXGW-TABLE]', tables.splice(0, 1)[0] + '<p></p>');
                    }
                }
                editor.insertContent(tempStr);
            }
        }
    });
}
/**
 * 获取编辑器内容：[文本内容,图片,表格]
 * @returns {(string|any[])[]}
 */
function getEditorContents() {
    var _html = getEditorContentHtml();
    var orghtml = _html;
    var _img = new RegExp('<img[\\s\\S]*?>', 'gi');
    var _table = new RegExp('<table[\\s\\S]*?</table>', 'gi');
    _html = _html.replace(_img, '<span>[WXGW-IMAGE]</span>');
    _html = _html.replace(_table, '<span>[WXGW-TABLE]</span>');

    var _imgs = tinymce.get('textareaDoc').dom.select('img');
    var images = new Array();
    for(var i = 0; i < _imgs.length; i++) {
        images.push(_imgs[i].outerHTML);
    }
    var _tables = tinymce.get('textareaDoc').dom.select('table');
    var tables = new Array();
    for(var i = 0; i < _tables.length; i++) {
        tables.push(_tables[i].outerHTML);
    }
    // 直接用html.body.innerText会导致有<br>被替掉，所以还是用tinymce的api获取文本
    tinymce.get('textareaDoc').setContent(_html);
    var text = getEditorContentText();
    tinymce.get('textareaDoc').setContent(orghtml);
    return [text, images, tables];
}

function addTTSButton(editor) {
    Artery.request({
        url: "api/getUIConfig",
        type: 'GET',
        data: {
            tenantCode: Artery.params.tenantCode ? Artery.params.tenantCode : ""
        },
        success: function (data) {
            if(data !== "" && data !== undefined){
                let jss = JSON.parse(data);
                for(var i = 0; i < jss.length; i++){
                    if(jss[i]['group'] === "gwdj" && jss[i]['showtype'] === "1"){
                        editor.ui.registry.addButton('tts', {
                            icon: 'langdu', // export export-word
                            tooltip: '下载音频',
                            onAction: () => {
                                Artery.mask(
                                    '正在转换音频...', // 提示信息
                                    0, // 延迟加载遮罩时间，必填
                                    {
                                        showIcon: true, // 是否显示loading图标
                                        styleType: 'small' // 遮罩类型
                                    })
                                Artery.request({
                                    url: "api/v1/tts/synthetise",
                                    type: 'POST',
                                    contentType: 'application/json;charset=UTF-8',
                                    data: {
                                        samplerate:	16000,
                                        language:	1,
                                        speed:	0,
                                        volume:	9,
                                        forma:	"wav",
                                        voice_name:	"1001",
                                        text:	getEditorContentText(),
                                        version:	"3.0.0.8",
                                        arch:	"x86",
                                        ostype:	"win",
                                        osversion:	"10.0 (Build 19045)",
                                        uuid:	getUuid(),
                                        loginId: Artery.params.uCp ? Artery.params.uCp : "",
                                        token:	Artery.params.vKt,
                                        tenantCode: Artery.params.tenantCode ? Artery.params.tenantCode : ""
                                    },
                                    success: function (response) {
                                        if(response !== undefined && response !== "" && response.indexOf("status") > -1){
                                            let rets = JSON.parse(response);
                                            if(rets.status === 2000){
                                                let id  = rets.commonFileId;
                                                Artery.download(
                                                    "api/v1/file/download/" + id,
                                                    {
                                                        data: {
                                                            fileName: Artery.get('textHeaderTitle').getValue(),
                                                        }, // 请求参数
                                                        mask: true, // 是否有遮罩，默认为true
                                                        maskTip: '正在转换音频...', // 遮罩提示信息，默认为 正在下载...
                                                        success: function () {
                                                            Artery.unmask();
                                                            Artery.alert.success("下载成功")
                                                        },  // 成功回调
                                                        error: function () {
                                                            Artery.unmask();
                                                            Artery.alert.warning("下载失败")
                                                        } // 失败回调
                                                    })
                                            }else{
                                                Artery.unmask();
                                                Artery.alert.warning("TTS服务异常");
                                            }
                                        }else{
                                            Artery.unmask();
                                            Artery.alert.warning("转写音频失败");
                                        }
                                    }
                                })
                            }
                        });
                        return
                    }
                }
            }
        }
    })
}

function updateRegionState() {
    var lh = $.cookie("wgbj_lh");
    lh === 'false' ? showRegionLeft() : hideRegionLeft();
    var rh = $.cookie("wgbj_rh");
    rh === 'true' ? hideRegionRight() : showRegionRight();
}

function initSplitEvent() {
    var handleMouseUp = function (event) {
        Artery.get("splitMask").hide();
        document.onmousemove = null;
        document.onmouseup = null;
        // 存储左右布局的宽度信息到coolie中
        var leftWidth = Artery.get("atyRegionLeft").el.css("width");
        $.cookie("wgbj_lw", leftWidth);
        var rightWidth = Artery.get("atyRegionRight").el.css("width");
        $.cookie("wgbj_rw", rightWidth);
    };

    var splitLeft = Artery.get("splitLeft");
    var handleLeftMove = function (event) {
        Artery.get("splitMask").show();
        splitLeft.startOffset = event.pageX;
        splitLeft.startWidth = parseFloat(Artery.get("atyRegionLeft").el.css("width"));
        document.onmousemove = function (event,) {
            var offset = splitLeft.startOffset - event.pageX;
            var width = splitLeft.startWidth - offset;
            if (width < 315) {
                width = 315;
            }
            updateRegionLeftWidth(width);
        };
        document.onmouseup = handleMouseUp;
    };
    if(splitLeft) {
        splitLeft.on("mousedown", handleLeftMove);
    }

    var splitRight = Artery.get("splitRight");
    var handleRightMove = function (event) {
        Artery.get("splitMask").show();
        splitRight.startOffset = event.pageX;
        splitRight.startWidth = parseFloat(Artery.get("atyRegionRight").el.css("width"));
        document.onmousemove = function (event) {
            var offset = splitRight.startOffset - event.pageX;
            var width = splitRight.startWidth + offset;
            if (width < 430) {
                width = 430;
            }
            updateRegionRightWidth(width);
        };
        document.onmouseup = handleMouseUp;
    };
    splitRight.on("mousedown", handleRightMove);
}

/**
 * 解析用户信息
 */
function parseUserInfo() {
    if (Artery.customParams && Artery.customParams.userInfo) {
        _PAGEDATA.user.userId = Artery.customParams.userInfo.userId;
        _PAGEDATA.user.username = Artery.customParams.userInfo.username;
        _PAGEDATA.user.deptId = Artery.customParams.userInfo.deptId;
        _PAGEDATA.user.corpId = Artery.customParams.userInfo.corpId;
        _PAGEDATA.user.tenantCode = Artery.customParams.userInfo.tenantCode;
    }
}

function getDeepSeekInfo(){
    Artery.request({
        url: 'web/wg/wgbj/getDeepSeekStatus',
        async: false,
        success: function (data, textStatus, response, cfg){
            if(Artery.get("atySwitchDs") !== undefined){
                Artery.get("atySwitchDs").setValue(data)
                useDeepThinking = Artery.get("atySwitchDs").getValue()
                console.log("设置状态的值是", useDeepThinking);
            }else{
                useDeepThinking = data;
                console.log("设置状态的值是", useDeepThinking);
            }
        },
        error: function (response, textStatus, errorThrown, options) {
            console.log(response)
        }
    })
}


/**
 * 检查配置开关情况
 */
function checkUIConfig() {
    _uiconfig.gwzk = 'uiconfig' in Artery.customParams && 'gwzs' in Artery.customParams.uiconfig && Artery.customParams.uiconfig.gwzs === '1';
    _uiconfig.ai = 'uiconfig' in Artery.customParams && 'gwznzs' in Artery.customParams.uiconfig && Artery.customParams.uiconfig.gwznzs === '1';
}

/**
 * 检查其他配置
 */
function checkOtherConfig() {
    // 设置按钮的显示隐藏状态：写作素材===>先注掉
    /*Artery.request({
        url: 'zhixie/pluginInfo',
        type: 'GET',
        async: false,
        data: {
            tenantCode: _PAGEDATA.user.tenantCode
        },
        success: function (data, textStatus, response, cfg) {
            if (Artery.isTrue(data.xzsc)) {
                Artery.get('btnNavXzsc').show();
            }
        },
        error: function (response, textStatus, errorThrown, options) {
            console.log(response)
        }
    });*/
    // 获取配置信息
    Artery.request({
        url: 'zhixie/userConfig',
        type: 'GET',
        async: false,
        success: function (result, textStatus, response, cfg) {
            if (!result.success) {
                console.log(result.msg);
                return;
            }
            if (result.data) {
                _PAGEDATA.setting.zdssjg = result.data.zdssjg;
                _PAGEDATA.setting.xssxw = result.data.xssxw;
                // _PAGEDATA.setting.xssxwsl = result.data.xssxwsl
            }
        },
        error: function (response, textStatus, errorThrown, options) {
            console.log(response)
        }
    });
}

/**
 * 加载默认iframe页面
 */
function loadDefaultIframePage() {
    var userInfo = btoa(encodeURI(JSON.stringify({"userId":_PAGEDATA.user.username,"corpId":_PAGEDATA.user.corpId,"deptId":_PAGEDATA.user.deptId,"tenantCode": _PAGEDATA.user.tenantCode})));
    if(_uiconfig.gwzk) {
        Artery.get('ifmZsjs').showLink('web/zx/zsjs?userInfo=' + userInfo);
    }else {
        if(Artery.get('btnNavXzsc')){
            Artery.get('btnNavXzsc').click();
        }
        if(Artery.get('ifmXzsc')){
            Artery.get('ifmXzsc').showLink('web/zx/xzsc?userInfo=' + userInfo);
        }
    }

    if(_uiconfig.ai) {
        if(Artery.params.audioId && Artery.params.name) {
            Artery.get('atyIframeQc').showLink('client/fdqc?audioId=' + Artery.params.audioId + '&name=' + Artery.params.name);
        }else {
            Artery.get('atyIframeQc').showLink('client/fdqc');
        }
    }else {
        Artery.get('btnNavGwjd').click();
        Artery.get('atyIframeJd').showLink('web/jd/jd');
        Artery.get('btnNavjfjc').click();
        Artery.get('atyIframeJc').showLink('web/xd/jfjc');
    }
}
/**
 * 加载其他iframe页面
 */
function loadOtherIframePage() {
    var userInfo = btoa(encodeURI(JSON.stringify({"userId":_PAGEDATA.user.username,"corpId":_PAGEDATA.user.corpId,"deptId":_PAGEDATA.user.deptId,"tenantCode": _PAGEDATA.user.tenantCode})));
    if(_uiconfig.gwzk) {
        Artery.get('ifmXzsc').showLink('web/zx/xzsc?userInfo=' + userInfo);
        if(Artery.get('ifmLdwh')){
            Artery.get('ifmLdwh').showLink('web/zx/ldwh?userInfo=' + userInfo);
        }
        Artery.get('ifmFanwen').showLink('web/fw/fwss?userInfo=' +  userInfo + '&type=fanwen');
        if(Artery.get('ifmGwsc')){
            Artery.get('ifmGwsc').showLink('web/zx/scj?userInfo=' +  userInfo);
        }
        if(Artery.get('ifmZlnr')){
            Artery.get('ifmZlnr').showLink('web/zx/scj?userInfo=' +  userInfo + '&type=zlnr');
        }
    }else {
        if(Artery.get('ifmLdwh')){
            Artery.get('ifmLdwh').showLink('web/zx/ldwh?userInfo=' + userInfo);
        }
        if(Artery.get('ifmGwsc')){
            Artery.get('ifmGwsc').showLink('web/zx/scj?userInfo=' +  userInfo);
        }
        if(Artery.get('ifmZlnr')){
            Artery.get('ifmZlnr').showLink('web/zx/scj?userInfo=' +  userInfo + '&type=zlnr');
        }

    }
    if(_uiconfig.ai) {
        Artery.get('atyIframeGx').showLink('client/gx');
        Artery.get('atyIframeGx2').showLink('client/gxrs');
        Artery.get('atyIframeJd').showLink('web/jd/jd');
        Artery.get('atyIframeJc').showLink('web/xd/jfjc');
        Artery.get('atyIframeLg').showLink('client/lg');
        Artery.get('atyIframePf').showLink('tj/evaPage');
        // Artery.get('atyIframeXy').showLink('chat?userInfo=' +  userInfo)
        // Artery.get('atyIframeXy').showLink('chat?userInfo=' +  userInfo)

    }
}

function getQueryVariable(key) {
    var e = document.location.toString();
    e = decodeURI(e);
    if(e.indexOf(key+'=') != -1) {
        e = e.substring(e.indexOf(key+'='));
        var vars = e.split("&");
        for (var i=0;i<vars.length;i++) {
            var pair = vars[i].split("=");
            if(pair[0] == key){
                return pair[1];
            }
        }
    }
    return undefined;
}

function getPageData (){
    return _PAGEDATA;
}

/**
 * 内嵌页面调用，设置显示配置信息
 * @param key
 * @param value
 */
function updateSettingInfo(key, value) {
    _PAGEDATA.setting[key] = value;
    writeSettingInfo();
}

function writeSettingInfo() {
    Artery.request({
        url: 'zhixie/setUserConfig',
        type: "POST",
        contentType: 'application/json;charset=UTF-8',
        data: {
            'zdssjg': _PAGEDATA.setting.zdssjg,
            'xssxw': _PAGEDATA.setting.xssxw,
            'xssxwsl': _PAGEDATA.setting.xssxwsl
        },
        success: function (data, textStatus, response, options) {
        },
        error: function (jqXHR, textStatus, errorThrown) {
        }
    });
}

/**
 * 打开控件时脚本(atyJDSettingModal)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyJDSettingModalOnShowClient(rc) {
    if ($.cookie(JD_COOKIE_KEY)) {
        JD_OBJ = JSON.parse($.cookie(JD_COOKIE_KEY))
    }
    renderOption();
}

/**
 * 【返回首页】
 * @param  rc 系统提供的AJAX调用对象
 */
function atyTextClickGoHomeBtn(rc) {
    saveDoc();
    var data = {
        "uCp": _PAGEDATA.params.uCp,
        "iQt": _PAGEDATA.params.iQt,
        "vKt": _PAGEDATA.params.vKt,
        "fromType": _PAGEDATA.params.fromType,
        "safemode": Artery.params.safemode
    };
    if('ksp_userId' in _PAGEDATA.params && _PAGEDATA.params.ksp_userId) {
        data.ksp_userId = _PAGEDATA.params.ksp_userId;
        data.ksp_userName = _PAGEDATA.params.ksp_userName;
        data.ksp_courtCode = _PAGEDATA.params.ksp_courtCode;
        data.ksp_courtName = _PAGEDATA.params.ksp_courtName;
    }
    Artery.open({
        "url": "web/index",
        "method": Artery.CONST.HTTP_METHOD.GET,
        "target": "_top",
        "data": data
    });
}


function renderOption() {
    Artery.get("atyRadioJHLY").setValue(JD_OBJ.trade);
    Artery.get("atyCheckBoxJHXX").setValue(JD_OBJ.check_type.join(","))
    Artery.get("atyRadioZXD").setValue(JD_OBJ.checkmode);
    if(Artery.get("atyRadioDMX")){
        Artery.get("atyRadioDMX").setValue(JD_OBJ.llmStatus === "0"? "1": JD_OBJ.llmStatus)
    }
}

/**
 * 关闭控件时脚本(atyJDSettingModal)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyJDSettingModalOnCloseClient(rc) {
    let trade = Artery.get("atyRadioJHLY").getValue();
    let check_type = Artery.get("atyCheckBoxJHXX").getValue();
    let checkmode = Artery.get("atyRadioZXD").getValue();
    let llmStatus = "0";
    if(Artery.get("atyRadioDMX")){
        llmStatus = Artery.get("atyRadioDMX").getValue();
    }

    JD_OBJ.trade = trade;
    JD_OBJ.check_type = check_type.length == 0 ? [] : check_type.split(",")
    JD_OBJ.checkmode = checkmode;
    JD_OBJ.llmStatus = llmStatus;


    writeCookie()
}

/**
 * 关闭控件时脚本(atyJDSettingModal)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyInputBlurNameEvent() {
    // 失去焦点事件
    lastMd5Sum = "";
    IS_AUTO_SAVING = false;
    saveDoc();
}

/**
 * 关闭控件时脚本(atyJDSettingModal)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyInputEnterEvent(rc) {
    if (event.keyCode == 13) {
        event.target.blur();
    }
}

function writeCookie(is_first) {
    if (is_first) {
        if ($.cookie(JD_COOKIE_KEY)) {
            return;
        }
    }
    // 7天失效
    $.cookie(JD_COOKIE_KEY, JSON.stringify(JD_OBJ), {expires: 7, path: '/IntelligentEditor'});
}

/**
 * 获取编辑器中当前选中的文本内容
 * 给内嵌子页面调用
 */
function getEditorSelectedText() {
    const editor = tinymce.get('textareaDoc');
    let content = editor ? editor.selection.getContent({format: 'text'}) : "";
    content = content.replace(/\n\n/g, "\n");
    return content;
}

/**
 * 获取编辑器中的内容（HTML格式）
 * 给内嵌子页面调用
 * @returns {*|string}
 */
function getEditorContentHtml() {
    const editor = tinymce.get('textareaDoc');
    return editor ? editor.getContent() : '';
}

/**
 * 获取编辑器中的内容（文本格式）
 * 给内嵌子页面调用
 * @returns {*|string}
 */
function getEditorContentText() {
    const editor = tinymce.get('textareaDoc');
    let content = editor ? editor.getContent({format: 'text'}) : '';
    content = content.replace(/\n\n/g, "\n");
    return content;
}


function getWordCount() {
    const editor = tinymce.get('textareaDoc');
    if (editor) {
        const bjPlugin = editor.plugins["wordcount"];
        if (bjPlugin) {
            return bjPlugin.getCount()
        }
    }
    return 0;
}

/**
 * 设置编辑器中的内容
 * 给内嵌子页面调用
 * @param content
 */
function setEditorContent(content) {
    const editor = tinymce.get('textareaDoc');
    if (editor) {
        editor.setContent(content);
    }
}


function setContentAndSelect(content) {
    const editor = tinymce.get('textareaDoc');
    if (editor) {
        let selectNode = editor.selection.getNode();

        if (selectNode.getAttribute("id") && selectNode.getAttribute("id").startsWith("p_")) {
            // editor.selection.setCursorLocation(selectNode, 0)
        }

        let pNode = editor.dom.create('p', {});
        pNode.innerText = content;
        let id = "p_" + getUuid();
        pNode.setAttribute("id", id);
        editor.selection.setNode(pNode);
        let ele = editor.dom.get(id);
        editor.selection.select(ele);

        // ele.removeAttribute("id");

    }
}

function getUuid() {
    var s = [];
    var hexDigits = "0123456789abcdef";
    for (var i = 0; i < 36; i++) {
        s[i] = hexDigits.substr(Math.floor(Math.random() * 0x10), 1);
    }
    s[14] = "4";  // bits 12-15 of the time_hi_and_version field to 0010
    s[19] = hexDigits.substr((s[19] & 0x3) | 0x8, 1);  // bits 6-7 of the clock_seq_hi_and_reserved to 01
    s[8] = s[13] = s[18] = s[23] = "-";

    var uuid = s.join("");
    return uuid;
}

var tempcount = 1;


function selectInEditor() {
    var editor = tinymce.get('textareaDoc');
    var startNode = editor.dom.select(".select-start");
    var endNode = editor.dom.select(".select-end");

    if ((!startNode || startNode.length < 1) && (!endNode || endNode.length < 1)) {
        return;
    }
    if (!startNode || startNode.length < 1) {
        startNode = endNode;
    }
    if (!endNode || endNode.length < 1) {
        endNode = startNode;
    }
    var selection = editor.getWin().getSelection();
    selection.removeAllRanges();

    var range = editor.dom.createRng();
    range.setStartBefore(startNode[0]);
    range.setEndAfter(endNode[0]);
    selection.addRange(range);

    startNode[0].classList.remove("select-start");
    endNode[0].classList.remove("select-end");
}

function selectInEditorV2() {
    var editor = tinymce.get('textareaDoc');
    var startNode = editor.dom.select(".select-start");
    var endNode = editor.dom.select(".select-end");

    if ((!startNode || startNode.length < 1) && (!endNode || endNode.length < 1)) {
        return;
    }
    if (!startNode || startNode.length < 1) {
        startNode = endNode;
    }
    if (!endNode || endNode.length < 1) {
        endNode = startNode;
    }
    var selection = editor.getWin().getSelection();
    selection.removeAllRanges();

    var range = editor.dom.createRng();
    range.setStartBefore(startNode[0]);
    range.setEndAfter(endNode[endNode.length-1]);
    selection.addRange(range);

    for(var i =0; i < startNode.length; i++) {
        startNode[i].classList.remove("select-start");
    }
    for(var i =0; i < endNode.length; i++) {
        endNode[i].classList.remove("select-end");
    }
    // startNode[0].classList.remove("select-start");
    // endNode[0].classList.remove("select-end");
}

/**
 * 插入内容到编辑器末尾，起草-生成全文调用
 * @param content 插入内容
 * @param isAppend 已选中时是否追加
 * @param isSelect 是否选中新插入内容
 * @param isEnd 是否结束插入，一次性插入传true，多次插入时最后一次传true
 * @param title 文章标题，只有起草页面调用且isEnd=true会传
 */
function insertToEditorAtEnd(content, isAppend = false, isSelect = true, isEnd = false, title = '') {
    var editor = tinymce.get('textareaDoc');
    if (!editor) {
        return;
    }
    if (isAppend) {
        editor.selection.collapse();// 取消选中，否则会把选中内容替掉
    }

    if(!content) {
        editor.selection.select(editor.getBody(), true);
        editor.selection.collapse(false);
        editor.insertContent(content);
        return;
    }

    // xman: 如果内容为空或以<table开头，则直接插入
    if (content.trim().startsWith('<table')) {
        editor.selection.select(editor.getBody(), true);
        editor.selection.collapse(false);
        editor.insertContent(content + '<p><br></p>');
        // 将光标移动到表格后
        const body = editor.getBody();
        const p = body.lastChild;
        editor.selection.setCursorLocation(p, 0);
        return;
    }

    if(content === '\n') {
        _liststart = false;
        applyTextPatterns(editor);
        _liststart = null;
        return;
    }

    if(content.startsWith('\n')) {
        _liststart = false;
        applyTextPatterns(editor);
        content = content.substring(1);
        _liststart = null;
    }

    content = trimContent(content);
    content = content.replace(/\n{1,}/g, '\n');

    // 处理列表问题：1和.被分开的情况
    if(/^[.]\s+/g.test(content) && getEditorContentText().endsWith('\n1')) {
        console.log('1 and . separate，do something');
        content = content.replace(/^[.]\s+/g, '.');
    }

    if(_liststart == null) {
        if(_listpattern.test(content)) {
            _liststart = true;
        }else {
            _liststart = false;
        }
    }

    var str = '';
    for(var i = 0; i < content.length; i++) {
        if (content[i] === '\n') {
            var str2 = trimContent(str);
            str2 = str2.replace(_seqpattern, function (match, group1, group2) {
                return group1+group2;// 以'\d. '开头的行处理成'\d.'
            });
            if(isSelect) {
                str2 = addClassToPTags(str2);
            }
            editor.selection.select(editor.getBody(), true);
            editor.selection.collapse(false);
            editor.insertContent(str2);
            applyTextPatterns(editor);

            str = '';
            if(i + 1 < content.length) {
                if(_listpattern.test(trimContent(content.substring(i+1)))) {
                    _liststart = true;
                }else {
                    _liststart = false;
                }
            }else {
                _liststart = null;
            }
        }else {
            str = str + content[i];
        }
    }
    if (str.length > 0) {
        var str2 = trimContent(str);
        str2 = str2.replace(_seqpattern, function (match, group1, group2) {
            return group1+group2;// 以'\d. '开头的行处理成'\d.'
        });
        if(isSelect) {
            str2 = addClassToPTags(str2);
        }
        editor.selection.select(editor.getBody(), true);
        editor.selection.collapse(false);
        editor.insertContent(str2);
        applyTextPatterns(editor);
        editor.execCommand('delete');
    }

    if(isSelect) {
        selectInEditorV2();
    }

    if(isEnd) {
        _liststart = null;
        if(title && title.length > 0 && Artery.get("textHeaderTitle").getValue() === '无标题文档') {
            Artery.get("textHeaderTitle").setValue(title);
        }
    }
}

/**
 * 获取编辑器中当前选中的文本内容
 * 给内嵌子页面调用
 * @param content 插入内容
 * @param isAppend 已选中时是否追加
 * @param isSelect 是否选中新插入内容
 * @param isEnd 是否结束插入，一次性插入传true，多次插入时最后一次传true
 * @param title 文章标题，只有起草页面调用且isEnd=true会传
 */
function insertToEditor(content, isAppend = false, isSelect = true, isEnd = false, title = '') {
    var editor = tinymce.get('textareaDoc');
    if (!editor) {
        return;
    }
    if (isAppend) {
        editor.selection.collapse();// 取消选中，否则会把选中内容替掉
    }

    if (!content) {
        editor.insertContent(content);
        return;
    }

    if(content === '\n') {
        _liststart = false;
        applyTextPatterns(editor);
        _liststart = null;
        return;
    }

    if(content.startsWith('\n')) {
        _liststart = false;
        applyTextPatterns(editor);
        content = content.substring(1);
        _liststart = null;
    }

    content = trimContent(content);
    content = content.replace(/\n{1,}/g, '\n');

    // 处理列表问题：1和.被分开的情况
    if(/^[.]\s+/g.test(content) && getEditorContentText().endsWith('\n1')) {
        console.log('1 and . separate，do something');
        content = content.replace(/^[.]\s+/g, '.');
    }

    if(_liststart == null) {
        if(_listpattern.test(content)) {
            _liststart = true;
        }else {
            _liststart = false;
        }
    }

    var str = '';
    for(var i = 0; i < content.length; i++) {
        if (content[i] === '\n') {
            var str2 = trimContent(str);
            str2 = str2.replace(_seqpattern, function (match, group1, group2) {
                return group1+group2;// 以'\d. '开头的行处理成'\d.'
            });
            if(isSelect) {
                str2 = addClassToPTags(str2);
            }
            editor.insertContent(str2);
            applyTextPatterns(editor);

            str = '';
            if(i + 1 < content.length) {
                if(_listpattern.test(trimContent(content.substring(i+1)))) {
                    _liststart = true;
                }else {
                    _liststart = false;
                }
            }else {
                _liststart = null;
            }
        }else {
            str = str + content[i];
        }
    }
    if (str.length > 0) {
        var str2 = trimContent(str);
        str2 = str2.replace(_seqpattern, function (match, group1, group2) {
            return group1+group2;// 以'\d. '开头的行处理成'\d.'
        });
        if(isSelect) {
            str2 = addClassToPTags(str2);
        }
        editor.insertContent(str2);
        applyTextPatterns(editor);
        editor.execCommand('delete');
    }

    if(isSelect) {
        selectInEditorV2();
    }

    if(isEnd) {
        _liststart = null;
        if(title && title.length > 0 && Artery.get("textHeaderTitle").getValue() === '无标题文档') {
            Artery.get("textHeaderTitle").setValue(title);
        }
    }
}
/**替换行内容开头的空格，会影响markdown语法生效*/
function trimContent(str) {
    str = str.replace(/^[ \s]+/g, '');
    return str;
}
/**触发tinymce格式化内容（类似于markdown）*/
function applyTextPatterns(editor) {
    editor.fire('keydown', {'key':'enter','keyCode':13})
    if(_liststart) {// 如果是列表类型，需要再次回车，否则都会变成列表项了
        editor.execCommand('mceInsertNewLine');
    }
}

function addClassToPTags(htmlString) {
    var div = document.createElement('div');
    div.innerHTML = htmlString;
    var childNodes = div.childNodes;
    var firstChild = childNodes[0];
    if (firstChild.nodeType === Node.TEXT_NODE) {
        var span = document.createElement('span');
        span.textContent = firstChild.nodeValue;
        div.replaceChild(span, firstChild);
        span.classList.add('select-start');
    } else {
        firstChild.classList.add('select-start');
    }
    let lastChild = childNodes[childNodes.length - 1];
    if (lastChild.nodeType === Node.TEXT_NODE) {
        var span = document.createElement('span');
        span.textContent = lastChild.nodeValue;
        div.replaceChild(span, lastChild);
        span.classList.add('select-end');
    } else {
        lastChild.classList.add('select-end');
    }
    return div.innerHTML;
}

/**
 * 为编校获取编辑器中的文本格式
 * 给内嵌子页面调用
 * @returns {*|string}
 */
function getEditorContentTextForBj() {
    return callBjPlugin("getContent");
}

/**
 * 高亮所有给定位置，并默认选中第一个
 * @param positions 所有需要高亮的位置
 * [
 *    {
 * 		start: 10,
 * 		finish: 12,
 * 	    id: 1
 * 	},
 *    {
 * 		start: 72,
 * 		finish: 75
 * 	    id: 2
 * 	},
 *    {
 * 		start: 81,
 * 		finish: 83
 * 	    id: 3
 * 	},
 * ]
 */
function highLightInEditor(positions) {
    callBjPlugin("highLight", positions);
}

/**
 * 选中指定位置
 * @param id 全文起始位置
 */
function selectIndexInEditor(id) {
    callBjPlugin("selectIndex", id);
}

/**
 * 清除所有高亮
 * @param keepEditorSelection 是否保持编辑器选择状态
 */
function cleanInEditor(keepEditorSelection) {
    callBjPlugin("clean", keepEditorSelection);
}

/**
 * 清除指定高亮
 * @param id 全文起始位置
 */
function cleanIndexInEditor(id) {
    callBjPlugin("cleanIndex", id);
}

/**
 * 替换指定内容
 * @param id 全文起始位置
 * @param text 替换文本
 */
function replaceInEditor(id, text) {
    callBjPlugin("replace", id, text);
}

/**
 * 调用编辑器的编校插件方法
 * @param method
 * @param params
 */
function callBjPlugin(method, ...params) {
    const editor = tinymce.get('textareaDoc');
    if (!editor) {
        return;
    }
    const bjPlugin = editor.plugins["gwbj"];
    if (!bjPlugin) {
        return;
    }
    return bjPlugin[method](...params);
}

/**
 * 显示左侧布局
 */
function triggerRegionLeft() {
    var lh = $.cookie("wgbj_lh");
    lh === 'false' ? hideRegionLeft() : showRegionLeft();
}

/**
 * 显示左侧布局
 */
function showRegionLeft() {
    var lw = $.cookie("wgbj_lw");
    if (lw === null || lw === undefined) {
        lw = "450px";
    }
    if (parseInt(lw) < 315) {
        lw = "315px";
    }
    updateRegionLeftWidth(lw);
    // Artery.get("iconHideLeft").show();
    $('.nav-wrapper-gwzlk').removeClass("hidden");
    $.cookie("wgbj_lh", false);
}

/**
 * 隐藏左侧布局
 */
function hideRegionLeft() {
    updateRegionLeftWidth("68px");
    $('.nav-wrapper-gwzlk').addClass("hidden");
    // Artery.get("iconHideLeft").hide();
    $.cookie("wgbj_lh", true);
}

/**
 * 更新左侧布局宽度
 * @param offset
 */
function updateRegionLeftWidth(width) {
    if(Artery.customParams.uiconfig.gwzs=="2") {
        width = 0
    }
    $("#atyRegionLayoutByqef").css("padding-left", width);
    $("#atyRegionLeft").css("width", width);
}

/**
 * 显示右侧布局
 */
function triggerRegionRight() {
    var rh = $.cookie("wgbj_rh");
    rh === 'false' ? hideRegionRight() : showRegionRight();
}

/**
 * 显示右侧布局
 */
function showRegionRight() {
    var rw = $.cookie("wgbj_rw");
    if (rw === null || rw === undefined) {
        rw = "525px";
    }
    if (parseInt(rw) < 310) {
        rw = "310px";
    }
    updateRegionRightWidth(rw);

    $('.nav-wrapper-gwzx').removeClass("hidden");
    // Artery.get("iconHideRight").show();
    $.cookie("wgbj_rh", false);
}

/**
 * 隐藏右侧布局
 */
function hideRegionRight() {
    updateRegionRightWidth("68px");
    $('.nav-wrapper-gwzx').addClass("hidden");
    // Artery.get("iconHideRight").hide();
    $.cookie("wgbj_rh", true);
}

/**
 * 更新右侧布局宽度
 * @param offset
 */
function updateRegionRightWidth(width) {
    $("#atyRegionLayoutByqef").css("padding-right", width);
    $("#atyRegionRight").css("width", width);
}

/**
 * 定时保存
 */
function autoSaveAtInterval() {
    if('safemode' in Artery.params && Artery.params.safemode === 'true') {
        return;
    }
    setInterval(saveDoc, INTERVAL);
}


function preloadEditorText() {
    // 如果是范文详情页点应用按钮，那么不管是否安全模式，都要初始化内容
    if(!('fwid' in Artery.params) && 'safemode' in Artery.params && Artery.params.safemode === 'true') {
        return;
    }
    let param = {
        "id": Artery.params.id,
        "fwid": Artery.params.fwid,
        "initContent": getEditorContentHtml()
    }
    Artery.request({
        url: ARTERY_GLOBAL.CONTEXT_PATH + '/web/api/wdgj/getWdgj',
        method: "POST",
        data: param,
        async: false,
        dataType: "json",
        success: function (data) {
            if (data.code == 200) {
                Artery.params.id = data.fileId;
                let fileName = "无标题文档";
                if (data.fileName) {
                    fileName = data.fileName;
                }
                Artery.get("textHeaderTitle").setValue(fileName);

                setEditorContent(data.text);
                // 初始打开时，处理一下，否则一直撤销会清空文本
                const editor = tinymce.get('textareaDoc');
                editor.undoManager.reset();
            } else {
                setEditorContent("");
            }
        },
        error: function (err, msg) {
            console.log(err, msg);
        }
    });
}

async function saveDoc(callback) {
    if('safemode' in Artery.params && Artery.params.safemode === 'true') {
        return;
    }

    let currentMd5Sum = $.md5(getEditorContentHtml())

    if (lastMd5Sum == currentMd5Sum) {
        if(callback) {
            callback();
        }
        return;
    }

    lastMd5Sum = currentMd5Sum;

    // 是否正在保存
    if (IS_AUTO_SAVING == true) return;
    IS_AUTO_SAVING = true;

    let content = getEditorContentText();
    let wordcount = getWordCount();
    let fileName = Artery.get("textHeaderTitle").getValue();
    /*let title = Artery.get("textHeaderTitle").getValue();
    if (title === "无标题文档" && content.trim().length > 0) {
        fileName = content.substring(0, max_file_name_length)
    } else {
        fileName = title.substring(0, max_file_name_length);
    }

    Artery.get("textHeaderTitle").setValue(fileName);*/

    let param = {
        "htmlText": getEditorContentHtml(),
        "id": Artery.params.id,
        fileName: fileName,
        abbrcontent: content.substring(0, max_abbr_content_length),
        wordcount: wordcount
    }
    let element = Artery.get("textHeaderSaveTips");
    element.show();
    element.setText("正在保存...");
    element.el.removeClass('saved');

    Artery.request({
        url: ARTERY_GLOBAL.CONTEXT_PATH + '/web/api/wdgj/saveWdgj',
        method: "POST",
        data: param,
        async: true,
        dataType: "json",
        success: async function (data) {
            if (data.code == 200) {
                let date = new Date();
                let hour = date.getHours().toString().padStart(2, "0")
                let minute = date.getMinutes().toString().padStart(2, "0")
                setTimeout(() => {
                    element.setText(`${hour}:${minute} 保存成功`);
                    element.el.addClass('saved');
                }, 500)
                Artery.params.id = data.fileId;
                // await setTimeIntervalTips(element);
            } else {
                element.setText("保存失败");
            }
            IS_AUTO_SAVING = false;
            if(callback) {
                callback();
            }
        },
        error: function (err, msg) {
            IS_AUTO_SAVING = false;
            element.setText("保存失败");
        }
    });

}


async function setTimeIntervalTips(element) {

    const sleep = (duration) => {
        return new Promise(resolve => setTimeout(resolve, duration));
    }
    // 先等1s
    await sleep(1500);

    let count = parseInt(INTERVAL / 1000);

    for (let i = 0; i < count; i++) {
        await sleep(1000);
        $(element).text(`${count - i}s 后自动保存`);
    }
    await sleep(500);

    $(element).css("display", "block").text("正在保存...");
    $(element).removeClass('saved');
}

/**
 * 点击时触发客户端脚本(iconHideLeft)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function iconHideLeftOnClickClient(rc) {
    hideRegionLeft(true);
}

/**
 * 点击时触发客户端脚本(iconHideRight)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function iconHideRightOnClickClient(rc) {
    if ($("#iconHideRight").hasClass("hide")) {
        showRegionRight(true);
    } else {
        hideRegionRight(true);
    }
}


/**
 * 点击菜单项时服务端脚本()
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function userCenterOnClickClient(rc) {
    rc.send(function (result) {
        if (result.success) {
            Artery.open({
                "url": result.url,
                "method": Artery.CONST.HTTP_METHOD.GET,
                "target": "_blank",
                "data": {
                    "isWeb": true
                }
            });
        } else {
            Artery.alert.error(result.msg);
            console.log(result.msg);
        }
    });
}

/**
 * 点击菜单项时服务端脚本()
 * 修改密码
 * @param  rc 系统提供的AJAX调用对象
 */
function onModifyPasswdClickClient(rc) {
    Artery.get('atyModalYnqmb').show()
}

/**
 * 点击菜单项时服务端脚本()
 * 退出服务
 * @param  rc 系统提供的AJAX调用对象
 */
function logoutonClickClient(rc) {
    rc.send({
        success: function (data, textStatus, jqXHR) {
            if (data !== undefined) {
                Artery.logout();
            }
        },
        error: function (jqXHR, textStatus, errorThrown) {

        }
    })
}


/**
 * 增加AIGC TIPS 功能
 */
function addAIGCTipS() {
    if(_uiconfig.ai) {
        $(".tox-statusbar").prepend(createMzsmEle());
        $(".tox-statusbar").append(createAIGCEle());
    }
}

function createMzsmEle() {
    let element = document.createElement("div");
    $(element)
        .css("margin", "0 20px")
        .css("color", " rgba(33,56,95,0.45")
        .text('以上内容由AI生成，仅供参考');
    return element;
}

function createAIGCEle() {
    let element = document.createElement("div");
    if(Artery.customParams.chatmode === 'thoughtless') {
        $(element)
            .css("background", "url(images/AIGC.png) no-repeat")
            .attr("id", "aigcTips")
            .css("margin", "0 20px")
            .css("display", "block")
            .css("cursor", "pointer")
            .css("width", "39px")
            .css("height", "13px")
            .css("position", "relative")
            .append(createTipEle())
            .hover(function (e) {
                $(e.target).find("#aigcTipText").css("display", "block");
            }, function (e) {
                $(e.target).find("#aigcTipText").css("display", "none");
            });
    }else {
        $(element)
            .css("background", "url(images/deepseeklogo.png) no-repeat")
            .attr("id", "aigcTips")
            .css("margin", "0 20px")
            .css("display", "block")
            .css("cursor", "pointer")
            .css("width", "100px")
            .css("height", "18px")
            .css("position", "relative")
            .css("background-size", "contain");
    }
    return element;
}

function createTipEle() {
    let element = document.createElement("div");
    let span = document.createElement("span");
    let arrow = document.createElement("div");
    let tipText = "免责声明：生成结果仅供您参考，内容由万象大模型输出，不代表我们的态度或观点。";

    $(arrow).css("position", "absolute")
        .css("height", "0")
        .css("width", "0")
        .css("right", "18px")
        .css("bottom", "-7px")
        .css("border", "5px solid transparent")
        .css("border-right-color", "#EBF6FF")
        // .css("border-right-color", "rgb(225 236 245)")
        .css("border-right-width", "10px")
        .css("border-bottom-width", "3px")
        .css("border-left-width", "1px")
        .css("transform", "rotate(270deg)")

    $(span)
        .css("opacity", "0.6")
        .css("font-family", "MicrosoftYaHei")
        .css("font-size", "12px")
        .css("color", "rgba(0,0,0,0.85)")
        .text(tipText)
        .css("font-weight", "400")

    $(element)
        .attr("id", "aigcTipText")
        .css("width", "314px")
        // .css("height", "54px")
        .css("position", "fixed")
        .css("transform", "translate(-275px, -75px)")
        .css("background", "#EBF6FF")
        .css("box-shadow", "0 2px 6px 0 rgba(151,165,181,0.50);")
        .css("border-radius", "10px")
        .css("padding", "8px 19px 14px")
        .css("display", "none")

    $(element).append(span);
    $(element).append(arrow);
    return element;
}


/**
 * 右侧公文智写相关
 */

function changeNavGwzx(iframeId, el) {
    if (_iframeId_gwzx === iframeId) {
        triggerRegionRight();
        return;
    }
    if(isRequestingGwzx()) {
        Artery.alert.warning("正在生成请稍后")
        return;
    }
    showRegionRight();
    el.addClass('btn-nav-item-select').siblings().removeClass('btn-nav-item-select');
    _iframeId_gwzx = iframeId;
    toggleGwzxIframesToShow(_iframeId_gwzx);
}

function toggleGwzxIframesToShow(idToShow) {
    var iframeIds = ["atyIframeQc", "atyIframeGx","atyIframeGx2", "atyIframeJd", "atyIframeLg", "atyIframeXy", "atyIframeJc", "atyIframePf"];
    for (var i = 0; i < iframeIds.length; i++) {
        var iframeId = iframeIds[i];
        if (iframeId === idToShow) {
            Artery.get(iframeId).show();
        } else {
            Artery.get(iframeId).hide();
        }
    }
}

function isRequestingGwzx() {
    var requesting = false;
    if(_iframeId_gwzx === 'atyIframeQc'
        && Artery.get("atyIframeQc")
        && Artery.get("atyIframeQc").getWindow()
        && Artery.get("atyIframeQc").getWindow().isRequesting) {
        requesting = Artery.get("atyIframeQc").getWindow().isRequesting();
    }
    if(_iframeId_gwzx === 'atyIframeGx'
        && Artery.get("atyIframeGx")
        && Artery.get("atyIframeGx").getWindow()
        && Artery.get("atyIframeGx").getWindow().isRequesting) {
        requesting = Artery.get("atyIframeGx").getWindow().isRequesting();
    }
    if(_iframeId_gwzx === 'atyIframeGx2'
        && Artery.get("atyIframeGx2")
        && Artery.get("atyIframeGx2").getWindow()
        && Artery.get("atyIframeGx2").getWindow().isRequesting) {
        requesting = Artery.get("atyIframeGx2").getWindow().isRequesting();
    }
    return requesting;
}

/**
 * 点击时脚本(btnNavGwqc)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavGwqcOnClickClient(rc) {
    changeNavGwzx("atyIframeQc", this.el);
}

/**
 * 点击时脚本(btnNavGxrs)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavGxrsOnClickClient(rc) {
    changeNavGwzx("atyIframeGx", this.el);
}

function btnNavGxrsOnClickClient2(rc) {
    changeNavGwzx("atyIframeGx2", this.el);
}

/**
 * 点击时脚本(btnNavPfjc)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavPfjcOnClickClient(rc) {
    changeNavGwzx("atyIframePf", this.el);
}


/**
 * 点击时脚本(btnNavLgfx)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavLgfxOnClickClient(rc) {
    changeNavGwzx("atyIframeLg", this.el);
}

/**
 * 点击时脚本(btnNavGwjd)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavGwjdOnClickClient(rc) {
    changeNavGwzx("atyIframeJd", this.el);
}

function btnNavJfjcOnClickClient(rc) {
    changeNavGwzx("atyIframeJc", this.el);
}

/**
 * 点击时脚本(atyButtonDlhvw)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonDlhvwOnClickClient(rc) {
    Artery.open({
        "method": Artery.CONST.HTTP_METHOD.POST,
        "url": 'doc/gwbd',
        "target": "_window",
        "config": {
            modal: true,
            hideFooter: true,
            escClosable: true,
            width: 960,
            height: 700,
            title: '公文比对',
            iframeId: 'gwbdIframe',
            className: 'gwbdModal',
            onClose: function (iframeId) {

            },
        },
        "data": {
            "openType": "web",
            "title": Artery.get('textHeaderTitle').getValue(),
            "content": getEditorContentText()
        }
    });
}

/**
 * 点击时脚本(btnNavZwbw)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavZwbwOnClickClient(rc) {
    changeNavGwzx("atyIframeXy", this.el);
}

/**
 * 左侧公文资料库相关
 */

/**
 * 为兼容
 * @returns {boolean}
 */
function isVersionSupportBdwgk() {
    return false;
}

/**
 * 为兼容
 * @returns {boolean}
 */
function isVersionSupport(currVer, supportVer) {
    return false;
}

function changeNavGwzlk(iframeId, el, reload) {
    if (_iframeId_gwzlk === iframeId) {
        triggerRegionLeft();
    } else {
        showRegionLeft();
        el.addClass('btn-nav-item-select').siblings().removeClass('btn-nav-item-select');
        _iframeId_gwzlk = iframeId;
        toggleGwzlkIframesToShow(_iframeId_gwzlk);
        if (reload === true) {
            Artery.get(_iframeId_gwzlk).reloadArea();
        }
    }
}

function toggleGwzlkIframesToShow(idToShow) {
    var iframeIds = ["ifmZsjs", "ifmXzsc", "ifmGwsc", "ifmZlnr", "ifmLdwh", "ifmFanwen"];
    for (var i = 0; i < iframeIds.length; i++) {
        var iframeId = iframeIds[i];
        if (iframeId === idToShow) {
            Artery.get(iframeId).show();
            if (Artery.get(iframeId).getWindow().updateSsjgState) {
                Artery.get(iframeId).getWindow().updateSsjgState()
            }
            if (Artery.get(iframeId).getWindow().updateSxwState) {
                Artery.get(iframeId).getWindow().updateSxwState()
            }
        } else {
            Artery.get(iframeId).hide();
        }
    }
}


/**
 * 点击时脚本(btnNavZsjs)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavZsjsOnClickClient(rc) {
    changeNavGwzlk("ifmZsjs", this.el);
}
/**
 * 点击时脚本(btnNavDljs)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavDljsOnClickClient(rc) {
    changeNavGwzlk("ifmDljs", this.el);
}

/**
 * 点击时脚本(btnNavXzsc)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavXzscOnClickClient(rc) {
    changeNavGwzlk("ifmXzsc", this.el);
}

/**
 * 点击时脚本(btnNavXzsc)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavLdwhOnClickClient(rc) {
    changeNavGwzlk("ifmLdwh", this.el);
}

/**
 * 点击时脚本(btnNavGwsc)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavGwscOnClickClient(rc) {
    changeNavGwzlk("ifmGwsc", this.el);
}

/**
 * 点击时脚本(btnNavZlnr)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavZlnrOnClickClient(rc) {
    changeNavGwzlk("ifmZlnr", this.el, true);
}

/**
 * 点击时脚本(atyButtonGolns)
 * 取消修改密码
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonGolnsOnClickClient(rc) {
    Artery.get('atyMessageNgsma').hide();
    Artery.getBySelector(".clear-tip").removeErrorTip();
    Artery.get("atyModalYnqmb").hide();
}



/**
 * 点击时脚本(atyButtonGbdhj)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonGbdhjOnClickClient(rc) {
    let oldpwd = Artery.get('atyInputIysmp');
    let newpwd = Artery.get('atyInputImxmp');
    let cnewpwd = Artery.get('atyInputGrubh');

    if (!oldpwd.isValid() || !cnewpwd.isValid() || !newpwd.isValid()) {
        return;
    }
    rc.put("oldPassword", 'ENCRYPT#' + Artery.encrypt(oldpwd.getValue()));
    rc.put("password", 'ENCRYPT#' + Artery.encrypt(newpwd.getValue()));
    rc.put("confirmPassword", 'ENCRYPT#' + Artery.encrypt(cnewpwd.getValue()));
    rc.send(function (result) {
        if (result !== undefined && result.isSuccess) {
            Artery.alert.info("修改密码成功，3S将自动退出，请重新登陆！");
            setTimeout(function () {
                Artery.logout();
            }, 3000);
        } else {
            if (result.errorMsg !== "") {
                Artery.get('atyMessageNgsma').setText(result.errorMsg);
                Artery.get('atyMessageNgsma').show();
            } else if (result.msg !== "") {
                Artery.get('atyMessageNgsma').setText(result.msg);
                Artery.get('atyMessageNgsma').show();
            }
        }
    });
}

/**
 * 点击时脚本(atyButtonYjfkQx)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonYjfkQxOnClickClient(rc) {
    Artery.get('atyMessageYjfk').hide();
    Artery.get("atyTextareaYj").removeErrorTip();
    Artery.get("atyInputLxfs").removeErrorTip();
    Artery.get("atyModalYjfk").hide();
}
/**
 * 点击时脚本(atyButtonYjfkQd)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonYjfkQdOnClickClient(rc) {
    let yjCmpt = Artery.get('atyTextareaYj');
    let lxfsCmpt = Artery.get('atyInputLxfs');

    if (!yjCmpt.isValid() || !lxfsCmpt.isValid()) {
        return;
    }
    rc.put("content", yjCmpt.getValue());
    rc.put("contact", lxfsCmpt.getValue());
    rc.send(function (result) {
        if (result.success) {
            Artery.alert.info("提交意见反馈成功！");
        } else {
            Artery.get('atyMessageYjfk').setText(result.msg);
            Artery.get('atyMessageYjfk').show();
        }
    });
}
/**
 * 点击控件时脚本()
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function imageonClickClient (rc){
    Artery.request({
        url: 'web/index/getUserInfo',
        type: 'GET',
        timeout: 60000,
        success: function (data, textStatus, response, options) {
            if (data != "") {
                window.open(Artery.addTamperProof(data), '_blank');
            }
            // Artery.alert.info(data);+
            // Artery.alert.info(Artery.addTamperProof(data));

        },
        error: function (jqXHR, textStatus, errorThrown) {
        },
        complete: function (jqXHR, textStatus) {
        }
    })
}

function showBenefitDialog() {
    Artery.get("atyModalBenefit").show();
}

/**
 * 点击时脚本()
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnTestOnclikc(rc) {
    Artery.get("atyModalBenefit").show();
}
/**
 * 点击时脚本(atyButtonXdufu)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function btnNavFanwenOnClickClient(rc) {
    changeNavGwzlk("ifmFanwen", this.el);
}

/**
 * 在范文详情页面，点击'创作底稿'调用
 * @param fwid
 */
function insertFanwenToEditor(title, content) {
    Artery.get("textHeaderTitle").setValue(title);
    var editor = tinymce.get('textareaDoc');
    editor.insertContent(content);
    $(editor.dom.doc.body).find('p').css('line-height', '1.5');
    var dataMceStyle = $(editor.dom.doc.body).find('p').attr('data-mce-style');
    $(editor.dom.doc.body).find('p').attr('data-mce-style', dataMceStyle+'line-height:1.5;')
}
/**
 * 点击时脚本(atyButtonOadrg)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonOadrgOnClickClient(rc) {
    saveDoc();
    var data = {
        "uCp": _PAGEDATA.params.uCp,
        "iQt": _PAGEDATA.params.iQt,
        "vKt": _PAGEDATA.params.vKt,
        "fromType": _PAGEDATA.params.fromType,
        "safemode": Artery.params.safemode
    };
    if('ksp_userId' in _PAGEDATA.params && _PAGEDATA.params.ksp_userId) {
        data.ksp_userId = _PAGEDATA.params.ksp_userId;
        data.ksp_userName = _PAGEDATA.params.ksp_userName;
        data.ksp_courtCode = _PAGEDATA.params.ksp_courtCode;
        data.ksp_courtName = _PAGEDATA.params.ksp_courtName;
    }
    Artery.open({
        "url": "web/v2/znxz",
        "method": Artery.CONST.HTTP_METHOD.GET,
        "target": "_self",
        "data": data
    });
}

/**
 * 当前标题，公文比对页面调用
 */
function getTitle() {
    return Artery.get('textHeaderTitle').getValue();
}
/**
 * 值改变时脚本(atySwitchDs)
 * 切换是否使用deepseek
 * @param  rc 系统提供的AJAX调用对象
 * @param  newValue 控件改变后的新值
 * @param  oldValue 控件改变前的旧值
 */
function atySwitchDsOnChangeClient (rc, newValue, oldValue){
    rc.put('useDeepSeek', newValue);
    rc.send(function(result) {
        console.log(result)
        useDeepThinking = newValue;
    });
}



function getUseDeepSeekStatus(){
    // if(useDeepThinking === undefined){
        Artery.request({
            url: 'web/wg/wgbj/getDeepSeekStatus',
            async: false,
            success: function (data, textStatus, response, cfg) {
                if (Artery.get("atySwitchDs") !== undefined) {
                    Artery.get("atySwitchDs").setValue(data)
                    useDeepThinking = Artery.get("atySwitchDs").getValue()
                    console.log("设置状态的值是", useDeepThinking);
                } else {
                    useDeepThinking = data;
                    console.log("设置状态的值是", useDeepThinking);
                }
            },
            error: function (response, textStatus, errorThrown, options) {
                console.log(response)
                console.log("获取状态失败返回false");
                useDeepThinking = false;
            }
        })
    // }
    return useDeepThinking === undefined? false : useDeepThinking;
}

/**
 * 获取文档id，起草页面调用，存cookie用
 */
function getDocId() {
    return Artery.params.id;
}


function loading(){
    Artery.mask(
        '正在格式化...', // 提示信息
        0, // 延迟加载遮罩时间，必填
        {
            showIcon: true, // 是否显示loading图标
            styleType: 'small' // 遮罩类型
        })
}


function loading(msg){
    Artery.mask(
        msg, // 提示信息
        0, // 延迟加载遮罩时间，必填
        {
            showIcon: true, // 是否显示loading图标
            styleType: 'small' // 遮罩类型
        })
}

function hideLoading() {
    Artery.unmask();
}


/**
 * 评估文章
 * @param article
 * @param title
 */
function evaluArticle(article, title){
    loading("正在评估文章...");

    let dataStr = sessionStorage.getItem('evaReportData') || localStorage.getItem('evaReportData');
    if (dataStr) {
        try {
            let data = JSON.parse(dataStr);
            if(data.title === title && data.article === article){
                hideLoading();
                console.log("直接走缓存")
                Artery.get('atyModalPjmk').show();
                Artery.get('pjmlFrame').showLink("tj/evaReport")
                return
            }
        }catch (e){
            console.log(e)
        }
    }
    Artery.request({
        url: "client/fdqc/eva1/onClickServer",
        type: 'POST',
        contentType: 'application/json;charset=UTF-8',
        data: {
            "fullText": article,
            "title": title
        },
        success: function(data, textStatus, response, options) {
            // 展示模态框
            hideLoading();
            Artery.get('atyModalPjmk').show();
            // const iframe = document.getElementById('pjmlFrame');
            // iframe.onload = function() {
            //     iframe.contentWindow.postMessage(data, '*');
            // };

            sessionStorage.setItem('evaReportData', JSON.stringify(data));

            Artery.get('pjmlFrame').showLink("tj/evaReport")
        },
        error: function (jqXHR, textStatus, errorThrown) {
            hideLoading();
        },
        complete:  function (jqXHR, textStatus) {
        }
    })
}

function closeModal(){
    Artery.get('atyModalPjmk').hide();
    Artery.get('atyModalPjmk2').hide();
}

/**
 * 选中全文，起草页面调用
 */
function selectWholeText() {
    var editor = tinymce.get('textareaDoc');
    editor.selection.select(editor.getBody(), true);
}

function showTextModel(type){
    modeType = type;
    Artery.get('atyModalWzts').show();
}

function showJdModel(type){
    JdType = type;
    Artery.get('atyModalJdTs').show();
}

window.addEventListener('message', (event) => {
    // 验证消息来源
    if (event.origin === window.location.origin) {
        if (event.data === 'closeModal') {
            closeModal();
        }
        if(event.data === "rewrite"){
            if(Artery.get('atyIframeQc') && Artery.get('atyIframeQc').getWindow()){
                closeModal();
                Artery.get('atyIframeQc').getWindow().rewrite()
            }
        }
        if(event.data === "showEditModal"){
            // 显示atyModalPjmk2模态框
            Artery.get('atyModalPjmk2').show();
            Artery.get('pjmlFrame2').showLink("tj/evaRewrite");
        }

        if(event.data === "insert2Editor"){
            closeModal()
            var evaEditData = sessionStorage.getItem("evaEditData")
            let parsed = {};
            if (evaEditData) {
                try { parsed = JSON.parse(evaEditData); } catch(e) {}
            }
            let resultText = parsed.resultText;
            //插入到文本
            clearContent();
            insertToEditor(resultText);
            format();
        }
    }
});
/**
 * 点击确认按钮时脚本(atyModalWzts)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyModalWztsOnOkClient (rc){
    let data = {
        "name": "clearText",
        "type": modeType
    }
    Artery.get('atyIframeQc').getWindow().postMessage(data, '*');
    Artery.get('atyModalWzts').hide();
}

/**
 * 点击取消按钮时脚本(atyModalWzts)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyModalWztsOnCancelClient (rc){
    let data = {
        "name": "keepText",
        "type": modeType
    }
    Artery.get('atyIframeQc').getWindow().postMessage(data, '*');
    Artery.get('atyModalWzts').hide();

}


/**
 * 点击确认按钮时脚本(atyModalJdTs)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyModalJdTsOnOkClient (rc){
    let data = {
        "name": "startJd",
        "type": JdType
    }
    Artery.get('atyIframeJd').getWindow().postMessage(data, '*');
    Artery.get('atyModalJdTs').hide();
}

/**
 * 点击取消按钮时脚本(atyModalJdTs)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyModalJdTsOnCancelClient (rc){
    let data = {
        "name": "cancelJd",
        "type": JdType
    }
    Artery.get('atyIframeJd').getWindow().postMessage(data, '*');
    Artery.get('atyModalJdTs').hide();
}