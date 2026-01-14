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

    if(wordSwitch === true && Number(lengthSize) > 2000) {
        Artery.alert.warning("字数要求不应超过2000字");
        returnNewState(isChange)
        return;
    }

    setTimeout(function () {
        if (wpsinner) {
            if (Artery.getParentArtery().getParentWindow().wps.WpsApplication().Selection.Start === Artery.getParentArtery().getParentWindow().wps.WpsApplication().Selection.End) {
                if(!isChange){
                    sentence = "";
                }
            } else {
                sentence = Artery.getParentArtery().getParentWindow().wps.WpsApplication().Selection.Text;
                // xman:2026-01-14 如果选中内容为空，则获取文档内容
                if (sentence.length === 0 || sentence.trim().length === 0) {
                    var app = window.parent.wps.WpsApplication()
                    var doc = app.ActiveDocument
                    sentence = doc.Range(0, doc.Words.Count).Text;
                    isSelected = false;
                }
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
                    // if(!isChange){
                    //     sentence = "";
                    // }
                    // xman:2026-01-14 如果选中内容为空，则获取文档内容
                    window.parent.aiWriter.getDocumentContent("", (resultObj) => {
                        try {
                            const res = JSON.parse(resultObj);
                            if (res.ok) {
                                sentence = res.content;
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
                    isSelected = false;
                } else {
                    sentence = text;
                }
            });
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
            if (app.Selection.Start !== app.Selection.End) {
                doc.Range(app.Selection.Start, app.Selection.End).Delete();
            }
            var range = doc.Range(app.Selection.End, app.Selection.End);
            range.InsertBefore(text);
            app.Selection.SetRange(app.Selection.End + text.length, app.Selection.End + text.length)
        }
    } else if (isWeb) {
        if (window.parent.parent.insertToEditor) {
            window.parent.parent.insertToEditor(text, false, true, true);
        }
    } else {
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
    request.timeout = 300000;
    request.onreadystatechange = function () {
        callback(request);
    }
    request.onerror = function handleError() {
        console.log("request error");
    }
    request.ontimeout = function handleTimeout() {
        console.error('timeout of ' + request.timeout + ' ms exceeded');
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
