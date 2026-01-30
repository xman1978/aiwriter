/*********************************************
 * gxrss JS
 *
 * @author huayu
 * @date 2025-06-17
 *********************************************/

let selectedCdyqId = "atyContainerbchi";

let wpsinner = false;
let isWeb = false;

let _request = null;
let connecting = false;
let _id = "";

const THINKREG = /<\/?think>/g;

// 是否使用深度思考
let useDeepThinking = undefined;

let tenantId = undefined;
let tenantCode = undefined;
let loginId = undefined;

let sentence = "";
// xman:2026-01-14 是否选中内容
let isSelected = true;
let expansion = "";
let lengthSize = 400;
let type = "COMPRESS_SIMPLIFY";
let wordSwitch = false;

let exchange = false;
let _scrollToBottom = true;
let state = "start";


$(document).ready(function () {
    initZoom()
    // 监听改写结果滚动事件
    document.getElementById('atyContainerZswjd').onscroll = function (e) {
        if(e.target.scrollTop + e.target.offsetHeight + 1 >= e.target.scrollHeight) {
            _scrollToBottom = true;
        }else {
            _scrollToBottom = false;
        }
    }
    wpsinner = window.parent.parent.wpsinner;

    //todo 具体干吗用暂时不知道
    if (Artery.getParentArtery().getParentArtery() && 'uCp' in Artery.getParentArtery().getParentArtery().params) {
        loginId = Artery.getParentArtery().getParentArtery().params.uCp;
    }
    if (Artery.getParentArtery().getParentArtery() && 'tenantCode' in Artery.getParentArtery().getParentArtery().params) {
        tenantCode = Artery.getParentArtery().getParentArtery().params.tenantCode;
    }
    if (Artery.getParentArtery() && 'tenantId' in Artery.getParentArtery().params) {
        tenantId = Artery.getParentArtery().params.tenantId;
    }

    isWeb = window.parent.parent.isWeb;
})

/**
 * 选中时脚本(atyToggleLayoutCdyq)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  selectedItem 被选中的子控件对象
 */
function atyToggleLayoutCdyqOnSelectClient (rc,selectedItem){
    if(selectedCdyqId !== "" && selectedCdyqId !== null) {
        document.getElementById(selectedCdyqId).classList.remove("text-content-type-selected");
    }
    document.getElementById(selectedItem.id).classList.add("text-content-type-selected");
    selectedCdyqId = selectedItem.id;
    showClearBtn();
}

function atyTextExpandOnClick (rc){
    Artery.get('atyContainerExport').show();
    Artery.get('packupPull').show();
    Artery.get('exportPull').hide();
    var elementById = document.getElementById("atyContainerZswjd");
    if(elementById){
        if(!elementById.classList.contains('pull-res')){
            elementById.classList.add('pull-res');
        }
        if(elementById.classList.contains('export-res')){
            elementById.classList.remove('export-res');
        }
    }
}

function atyTextPackupOnClick (rc){
    Artery.get('atyContainerExport').hide();
    Artery.get('packupPull').hide();
    Artery.get('exportPull').show();
    var elementById = document.getElementById("atyContainerZswjd");
    if(elementById){
        if(!elementById.classList.contains('export-res')){
            elementById.classList.add('export-res');
        }
        if(elementById.classList.contains('pull-res')){
            elementById.classList.remove('pull-res');
        }
    }
}

function atyButtonStart() {
    Artery.get('toggleBtn').hide();
    atyTextPackupOnClick();
    exchange = false;
    prepareAndCall(false);
}

// xman:2026-01-30 获取Word文档正文内容（排除页眉、页脚、脚注等）
function getWpsContent() {
    const wdMainTextStory = 1;          // 主正文区域

    try {
        var doc = Artery.getParentArtery().getParentWindow().wps.WpsApplication().ActiveDocument;
        if (!doc) {
            Artery.alert.warning("请先打开一个 Word 文档！");
            return "";
        }

        var mainRange = doc.StoryRanges.Item(wdMainTextStory).Duplicate;

        // 收集所有目录（TOC）的起止位置，用于排除
        var tocRanges = [];
        for (var i = 1; i <= doc.TablesOfContents.Count; i++) {
            var toc = doc.TablesOfContents.Item(i);
            tocRanges.push({
                start: toc.Range.Start,
                end: toc.Range.End
            });
        }

        // 遍历主正文中的所有段落
        var paragraphs = mainRange.Paragraphs;
        var resultLines = [];

        for (var p = 1; p <= paragraphs.Count; p++) {
            var para = paragraphs.Item(p);
            var paraRange = para.Range;
            var start = paraRange.Start;
            var end = paraRange.End;

            // 跳过位于目录中的段落
            var skip = false;
            for (var j = 0; j < tocRanges.length; j++) {
                var tr = tocRanges[j];
                if (end > tr.start && start < tr.end) {
                    skip = true;
                    break;
                }
            }
            if (skip) continue;

            // 获取用户输入的文本（去除段落标记 \r）
            var text = paraRange.Text;
            if (text) {
                if (text.endsWith("\r\n")) {
                    text = text.slice(0, -2);
                } else if (text.endsWith("\r") || text.endsWith("\n")) {
                    text = text.slice(0, -1);
                }
            }

            // 获取该段落显示的编号（如 "1 ", "2.1 ", "• " 等）
            var listStr = "";
            try {
                listStr = paraRange.ListFormat.ListString || "";
            } catch (e) {
                listStr = "";
            }

            // 合并编号与文本
            var fullLine = listStr.trim() + (listStr.trim() ? " " : "") + text;

            // 跳过纯空白行和目录
            if (fullLine.trim() !== "" && fullLine.trim() !== "目录") {
                resultLines.push(fullLine);
            }
        }

        // 合并为完整文本
        var finalText = resultLines.join("\n");

        return finalText;

    } catch (e) {
        Artery.alert.warning("获取正文时出错：\n" + e.message);
        return "";
    }
}


function prepareAndCall(isChange) {
    if (connecting) {
        console.log("正在请求中，稍后再试");
        returnNewState(isChange);
        return;
    }
    
    expansion = Artery.get('textareaRequire').getValue();
    if(expansion.length >= 2500) {
        Artery.alert.warning("缩写要求过长");
        returnNewState(isChange);
        return ;
    }

    lengthSize = Artery.get('numeric').getValue();
    if(wordSwitch === true && (isNaN(lengthSize) || lengthSize === '' || lengthSize === null || lengthSize === undefined || !Number.isInteger(Number(lengthSize)) || Number(lengthSize) <= 0 )){
        Artery.alert.warning("字数要求请输入有效数字")
        returnNewState(isChange)
        return;
    }

    if(wordSwitch === true && Number(lengthSize) > 5000) {
        Artery.alert.warning("字数要求不应超过5000字");
        returnNewState(isChange)
        return;
    }

    setTimeout(function () {
        if (wpsinner) {
            if (Artery.getParentArtery().getParentWindow().wps.WpsApplication().Selection.Start === Artery.getParentArtery().getParentWindow().wps.WpsApplication().Selection.End) {
                // if(!isChange){
                //    sentence = "";
                // }
                // xman:2026-01-14 如果选中内容为空，则获取文档内容
                // var doc = Artery.getParentArtery().getParentWindow().wps.WpsApplication().ActiveDocument;
                // var rawText = doc.Content.Text;
                // sentence = rawText.replace(/\r\n|\r/g, "\n").trim(); // 移除尾部换行
                sentence = getWpsContent();
                isSelected = false;
            } else {
                sentence = Artery.getParentArtery().getParentWindow().wps.WpsApplication().Selection.Text;
                isSelected = true;
            }
            // 如果选中内容为空，则提示用户选中内容
            if (sentence.length === 0 || sentence.trim().length === 0) {
                Artery.alert.warning("请先选中编辑区域文本后重试");
                returnNewState(isChange)
                return;
            }
            if (sentence.length > 12000 && isSelected) {
                Artery.alert.warning("选择文本内容不可超过10K");
                returnNewState(isChange)
                return;
            }
            _prepareAndCall(isChange)
        } else if(isWeb) {
            if (window.parent.parent.getEditorSelectedText) {
                var text = window.parent.parent.getEditorSelectedText()
                if((text.length === 0 || text.trim().length === 0)) {
                    // if(!isChange){
                    //     sentence = "";
                    // }
                    // xman:2026-01-14 如果选中内容为空，则获取文档内容
                    sentence = window.parent.parent.getEditorContentText();
                    isSelected = false;
                } else {
                    sentence = text;
                    isSelected = true;
                }
            }
            if (sentence.length === 0 || sentence.trim().length === 0) {
                Artery.alert.warning("请先选中编辑区域文本后重试");
                returnNewState(isChange)
                return;
            }
            if (sentence.length > 12000 && isSelected) {
                Artery.alert.warning("选择文本内容不可超过10K");
                returnNewState(isChange)
                return;
            }
            _prepareAndCall(isChange)
        } else {
            window.parent.parent.aiWriter.getSelectedText(false, function (resultObj) {
                var res = JSON.parse(resultObj);
                var text = res.text;
                if((text.length === 0 || text.trim().length === 0)) {
                    if(!isChange){
                        sentence = "";
                    }                    
                } else {
                    sentence = text;
                }
                isSelected = true;
            });
            // xman:2026-01-14 如果选中内容为空，则获取文档内容
            if (sentence.length === 0 || sentence.trim().length === 0) {
                window.parent.parent.aiWriter.getDocumentContent("", function (resultObj) {
                    try {
                        const res = JSON.parse(resultObj);
                        if (res.ok) {
                            sentence = res.content;
                            isSelected = false;
                        } else {
                            Artery.message.warning("获取文档内容失败");
                            returnNewState(isChange)
                            return;
                        }
                    } catch (error) {
                        Artery.message.warning("获取文档内容失败");
                        returnNewState(isChange)
                        return;
                    }
                });
            }
            setTimeout(function () {
                if (sentence.length === 0 || sentence.trim().length === 0) {
                    Artery.alert.warning("请先选中编辑区域文本后重试");
                    returnNewState(isChange)
                    return;
                }
                if (sentence.length > 10240 && isSelected) {
                    Artery.alert.warning("选择文本内容不可超过10000字");
                    returnNewState(isChange)
                    return;
                }
                _prepareAndCall(isChange)
        }, 200)
        }
    }, 200)
}

function _prepareAndCall(isChange) {
    Artery.get("clearBtn").hide();
    Artery.get("clearBtn2").show();
    connecting = true;
    Artery.get("atyContainerEzumh").show();
    showLoading();
    updateScroll(document.getElementById("atyContainerZswjd"));
    disabledAllBtn()

    let statrTime = new Date().getTime();
    let costTime = -1;
    Artery.get('atyTextDesc').setText("深度思考中");
    Artery.get('atyTextLoading').show()
    Artery.get('atytextThinkContent').setText("");
    let llm_mode = getUseDeepSeekStatus()
    document.getElementById("atyContainerXotmm").innerText = "";
    let cache = ""
    let lastPosition = 0;
    request(llm_mode, function (request) {
        if (request.responseText.length > 0 && !request.responseText.startsWith('权益错误：')) {
            let text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
            lastPosition = request.responseText.length;
            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                Artery.get('atyContainerThink').show();
                let array = splitThinkTag(text);
                for(let i = 0; i < array.length; i++){
                    if(array[i] === ''){
                        continue
                    }
                    if(array[i].indexOf("<think>") > -1){
                        Artery.get('atytextThinkContent').setText(Artery.get('atytextThinkContent').getText() + array[i].replace(THINKREG, ''));
                    }else{
                        cache += array[i]
                    }
                }
            }else{
                if(costTime === -1){
                    Artery.get('atyTextLoading').hide()
                    Artery.get('atyTextDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                }
                let tok = "";
                if(cache !== ''){
                    tok = cache + text;
                    cache = ''
                }else{
                    tok = text;
                }
                document.getElementById("atyContainerXotmm").innerText += tok;
            }
            updateScroll(document.getElementById("atyContainerZswjd"));
        }
        if (request.readyState === 4) {
            if(request.responseText.startsWith('权益错误：')) {
                Artery.getTopWindow().showBenefitDialog();
                connecting = false;
                returnNewState(isChange)
                return;
            }
            if($('#atyContainerXotmm').textContent === ""){
                Artery.alert.warning("没有写出合适的内容")
                connecting = false;
                returnNewState(isChange)
                return;
            }
            _result = document.getElementById("atyContainerXotmm").innerText

            Artery.get("atyButtonCompare").state = "bd";

            if (sentence.endsWith("") || sentence.endsWith("\r") || sentence.endsWith("\n")) {
                _result = _result + '\n';
            }
            connecting = false;
            hideLoading();
            enabledAllBtn();
            Artery.get('atyContainerCxsc').show();
            state = 'restart';
        }
    });
}

function getUseDeepSeekStatus(){
    Artery.request({
        url: 'web/wg/wgbj/getDeepSeekStatus',
        async: false,
        success: function (data, textStatus, response, cfg) {
            if (Artery.get("atySwitchDs") !== undefined) {
                Artery.get("atySwitchDs").setValue(data)
                useDeepThinking = Artery.get("atySwitchDs").getValue()
            } else {
                useDeepThinking = data;
            }
        },
        error: function (response, textStatus, errorThrown, options) {
            useDeepThinking = false;
        }
    })
    return useDeepThinking === undefined? false: useDeepThinking;
}

function showLoading() {
    Artery.get("atyContainerOdrgi").show();
}

function atyContainerRsCxsc_OnClient() {
    exchange = false;
    Artery.get('atyContainerCxsc').hide();
    prepareAndCall(true);
}

function enabledAllBtn() {
    document.querySelectorAll('.container-content-type').forEach(btn => {
        if(btn.classList.contains('text-content-type-disabled')) {
            btn.classList.remove('text-content-type-disabled');
        }
        btn.style.pointerEvents = '';
        btn.classList.remove('no-hover');
    })
    Artery.get('textareaRequire').enable();
    Artery.get('numeric').enable();
    restoreStrBtn(selectedCdyqId);
}

function disabledAllBtn() {
    document.querySelectorAll('.container-content-type').forEach(btn => {
        if(btn.classList.contains('text-content-type-selected')) {
            btn.classList.remove('text-content-type-selected');
            btn.classList.add("text-content-type-disabled");
        }
        btn.style.pointerEvents = 'none';
        btn.classList.add('no-hover');
    })
    Artery.get('textareaRequire').disable();
    Artery.get('numeric').disable();
}

/**
 * 点击时触发客户端脚本(atyIconNqpbg)
 * 切换icon
 * @param rc 系统提供的AJAX调用对象
 */
function showOrCloseThinkStep(){
    const divElement = document.getElementById('atyTextTs');
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        Artery.get('atycontainerTc').hide()
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        Artery.get('atycontainerTc').show()
    }
}

/**
 * 点击时脚本(atyButtonCompare)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonCompareOnClickClient(rc) {
    if (this.state === "qxbd") {
        document.getElementById("atyContainerXotmm").innerText = _result;
        this.state = "bd";
        this.setText("比对");
    } else {
        let dmp = new diff_match_patch();
        let diffs = dmp.diff_main(sentence.trim(), _result.trim());
        dmp.diff_cleanupSemantic(diffs);
        let diffHtml = diff_prettyHtml(diffs);
        $("#atyContainerXotmm").html(diffHtml);
        this.state = "qxbd";
        this.setText("取消比对");
    }
}

/**
 * 点击时触发客户端脚本(atyIconSffpy)（换一换）
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyIconSffpyOnClickClient(rc) {
    exchange = true;
    Artery.get('atyContainerCxsc').hide();
    prepareAndCall(true);
}

/**
 * 点击时触发客户端脚本(atyIconCvjbv)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyIconCvjbvOnClickClient(rc) {
    let textarea = document.createElement('textarea');
    textarea.setAttribute('style', 'position:fixed;top:0;left:0;opacity:0;z-index:-10;');
    textarea.value = document.getElementById("atyContainerXotmm").innerText;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
}

/**
 * 点击时触发客户端脚本(atyButtonRxvst)fan
 * @param  rc 系统提供的AJAX调用对象
 */

function atyButtonRxvstOnClickClient(rc) {
    text = _result.trim()
    if (wpsinner) {
        var app = Artery.getParentArtery().getParentWindow().wps.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc) {
            if (isSelected) {
                if (app.Selection.Start !== app.Selection.End) {
                    doc.Range(app.Selection.Start, app.Selection.End).Delete();
                }
            } else {
                // xman:2026-01-19 清空所有内容
                doc.Range(0, doc.Content.End).Delete();
            }
            var range = doc.Range(app.Selection.End, app.Selection.End);
            range.InsertBefore(text);
            app.Selection.SetRange(app.Selection.End + text.length, app.Selection.End + text.length);
        }
    } else if (isWeb) {
        if (!isSelected) {
            // xman:2026-01-19 清空所有内容，然后插入新内容
            window.parent.parent.clearContent();
        }
        if (window.parent.parent.insertToEditor) {
            window.parent.parent.insertToEditor(text, false, true, true);
        }
    } else {
        if (!isSelected) {
            // xman:2026-01-19 清空所有内容，然后插入新内容
            window.parent.parent.aiWriter.selectAllText();
            window.parent.parent.aiWriter.deleteSelectedText();
        }
        window.parent.parent.aiWriter.insertText(text, function (resultObj) {
            var res = JSON.parse(resultObj);
            if (res.ok) {
                console.log("插入成功");
            } else {
                console.log(res.message);
            }
        })
    }
}

function initZoom() {
    if ('fontZoom' in Artery.getParentArtery().getParentArtery().params) {
        document.body.style.zoom = parseFloat(Artery.getParentArtery().getParentArtery().params.fontZoom);
    } else {
        if (window.screen.width >= 2560) {
            document.body.style.zoom = 1.5;
        } else {
            document.body.style.zoom = 1;
        }
    }
}


/**
 * 将比对结果转变为HTML
 * @param diffs
 * @returns {string}
 */
function diff_prettyHtml(diffs) {
    let html = [];
    let pattern_amp = /&/g;
    let pattern_lt = /</g;
    let pattern_gt = />/g;
    let pattern_para = /\n/g;
    for (let x = 0; x < diffs.length; x++) {
        let op = diffs[x][0];    // Operation (insert, delete, equal)
        let data = diffs[x][1];  // Text of change.
        let text = data.replace(pattern_amp, '&amp;').replace(pattern_lt, '&lt;')
            .replace(pattern_gt, '&gt;').replace(pattern_para, '<br>');
        switch (op) {
            case DIFF_INSERT:
                html[x] = '<span class="result-text insert">' + text + '</span>';
                break;
            case DIFF_DELETE:
                html[x] = '<span class="result-text delete">' + text + '</span>';
                break;
            case DIFF_EQUAL:
                html[x] = '<span class="result-text equal">' + text + '</span>';
                break;
        }
    }
    return html.join('');
}

function request(llm_mode, callback) {
    _id = getUuid();
    let request = new XMLHttpRequest();
    let _method = 'POST';
    let _url = 'client/gxrss/chat';
    request.open(_method, _url, true);
    request.setRequestHeader('Content-Type', 'application/json; charset=UTF-8');
    request.setRequestHeader('Accept', 'application/json, text/plain, */*');
    
    // xman:2026-01-29 禁用XMLHttpRequest的内置超时，使用共用的自定义超时管理
    request.timeout = 0; // 禁用内置超时，使用自定义超时管理
    
    // xman:2026-01-29 使用共用的超时管理函数
    var timeoutManager = createCustomTimeoutManager(request, _url, _id, 3600000);
    var lastDataReceiveTime = Date.now();
    
    request.onreadystatechange = function () {
        /**
         * request.readyState
         * 0: 请求未初始化
         * 1: 服务器连接已建立
         * 2: 请求已接收
         * 3: 请求处理中
         * 4: 请求已完成，且响应已就绪
         */
        // xman:2026-01-29 当接收到数据时（readyState为3），重置超时计时器
        if (request.readyState === 3) {
            // 接收到流式数据，重置自定义超时计时器
            lastDataReceiveTime = Date.now();
            // 重置自定义超时计时器：清除旧的计时器，创建新的1小时计时器
            timeoutManager.resetCustomTimeout();
            var elapsedTime = Date.now() - request._startTime;
            // 每30秒记录一次数据接收日志，避免日志过多
            if (elapsedTime % 30000 < 1000) {
                console.log('[数据接收] 接收到流式数据，已运行: ' + Math.floor(elapsedTime/1000) + 's, 已重置自定义超时计时器（3600s）');
            }
        }
        // xman:2026-01-29 请求完成时清除自定义超时计时器并注销请求对象
        if (request.readyState === 4) {
            timeoutManager.cleanup();
            // 注销请求对象，避免内存泄漏
            if (typeof unregisterRequest === 'function' && request._getRequestFn) {
                unregisterRequest(request._getRequestFn);
            }
        }
        callback(request);
    }
    request.onerror = function handleError() {
        // xman:2026-01-29 记录请求错误日志
        var elapsedTime = request._startTime ? Date.now() - request._startTime : 0;
        console.error('[请求错误] 请求发生错误，已运行: ' + Math.floor(elapsedTime/1000) + 's, readyState: ' + request.readyState);
        console.error('[请求错误] 请求URL: ' + _url + ', 请求ID: ' + _id);
        // 清除自定义超时计时器并注销请求对象
        timeoutManager.cleanup();
        if (typeof unregisterRequest === 'function' && request._getRequestFn) {
            unregisterRequest(request._getRequestFn);
        }
        connecting = false;
    }
    request.ontimeout = function handleTimeout() {
        // xman:2026-01-29 记录请求超时日志（XMLHttpRequest内置超时，但我们已经禁用了）
        var elapsedTime = request._startTime ? Date.now() - request._startTime : 0;
        console.error('[请求超时-XMLHttpRequest] 超时时间: ' + request.timeout + 'ms, 已运行: ' + Math.floor(elapsedTime/1000) + 's, readyState: ' + request.readyState);
        console.error('[请求超时-XMLHttpRequest] 请求URL: ' + _url + ', 请求ID: ' + _id);
        // 清除自定义超时计时器并注销请求对象
        timeoutManager.cleanup();
        if (typeof unregisterRequest === 'function' && request._getRequestFn) {
            unregisterRequest(request._getRequestFn);
        }
        connecting = false;
    }

    let parentArtery = Artery.getParentArtery();
    if(getOptionText(selectedCdyqId, null) === '总结') {
        type = "COMPRESS_SUMMARY";
    } else {
        type = "COMPRESS_SIMPLIFY";
    }

    let params = {
        "id": _id,
        "sentence": sentence,
        "isSelected": isSelected,
        "exchange": exchange,
        "expansion": expansion,
        "lengthSize": lengthSize,
        "wordSwitch": wordSwitch,
        "tenantCode": tenantCode ? tenantCode : "",
        "loginId": loginId ? loginId : "",
        "tenantId":tenantId ? tenantId : "",
        "type": type,
        "llmMode": llm_mode,
        // 是否最高简版页面嵌入的
        "simple": 'simple' in parentArtery.getParentArtery().params && parentArtery.getParentArtery().params.simple === 'true'
    };

    request.send(JSON.stringify(params));
    _request = request;
    _scrollToBottom = true;
    // xman:2026-01-29 注册请求对象到共用心跳模块
    var getRequestFn = function() { return _request; };
    if (typeof registerRequest === 'function') {
        registerRequest(getRequestFn);
        // 将获取函数存储到request对象上，供注销时使用
        request._getRequestFn = getRequestFn;
    }
}

function updateScroll(e) {
    if(_scrollToBottom) {
        var scrollHeight = e.scrollHeight;
        var scrollTop = e.scrollTop;
        var clientHeight = e.clientHeight;
        if (scrollTop + clientHeight < scrollHeight) {
            e.scrollTop = scrollHeight - clientHeight;
        }
    }
}

/**
 * 停止按钮：点击时触发客户端脚本(atyIconXgeki)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyIconXgekiOnClickClient(rc) {
    // xman:2026-01-29 注销请求对象后再中止请求
    if (_request && typeof unregisterRequest === 'function' && _request._getRequestFn) {
        unregisterRequest(_request._getRequestFn);
    }
    _request.abort();
    _request = null;
    connecting = false;
    _result = document.getElementById("atyContainerXotmm").innerText;
    hideLoading();
}

/**
 * 值改变时脚本
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  newValue 控件改变后的新值
 * @param  oldValue 控件改变前的旧值
 */
function handleChange(rc, newValue, oldValue) {
    wordSwitch = newValue;
    showClearBtn();
    if(wordSwitch === false) {
        Artery.get('atyContainerZsyq').hide();
    } else {
        Artery.get('atyContainerZsyq').show();
    }
}

/**
 * 点击时脚本(clearBtn)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function onClearClickClient(rc) {
    // 清空所有选中状态
    document.querySelectorAll('.text-content-type-selected').forEach(element => {
        element.classList.remove('text-content-type-selected');
    });
    selectedCdyqId = "atyContainerbchi";
    document.getElementById("atyContainerbchi").classList.add("text-content-type-selected");
    expansion = "";
    Artery.get('textareaRequire').setValue(expansion);
    lengthSize = 400;
    if(Artery.get('numeric')){
        Artery.get('numeric').setValue(lengthSize);
    }
    type = "COMPRESS_SIMPLIFY";
    wordSwitch = false;
    Artery.get('switchChangeTest').setValue(wordSwitch);
    Artery.get('atyContainerZsyq').hide();
    Artery.get('clearBtn').hide();
    if(state === 'restart') {
        atyTextExpandOnClick();
        Artery.get("switchChangeTest").setValue(wordSwitch);
        Artery.get('atyContainerCxsc').hide();
        Artery.get('clearBtn2').hide();
        Artery.get("atyContainerEzumh").hide();
        Artery.get("toggleBtn").show();
        state = 'start';
        Artery.get('packupPull').hide();
        Artery.get('exportPull').hide();
    }
}

/**
 * 输入时脚本(textarea)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  value 输入后的值
 */
function keyupClickClient (rc, value){
    if(value.trim().length > 0){
        showClearBtn()
    }
}

function showClearBtn() {
    if(state === 'start'){
        Artery.get("clearBtn").show();
    }
}