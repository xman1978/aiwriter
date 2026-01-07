/*********************************************
 * fdqc JS
 *
 * @author huayu
 * @date 2024-01-29
 *********************************************/

var selectedGwwzId = "";// 选中的公文文种
var _files = {};// 已上传的文件
var _xcfiles = {};// 信创下已选择但未上传的文件
var requesting = false;// 标识是否请求中
var _request = null;
var wpsinner = false;// 是否嵌入，信创下嵌入到智写插件里
var isWeb = false; // 是否是web版本嵌入
var _id = "";// 标识最新一次起草的记录id
var _continueWriteTime = 0;// 没写完的情况续写次数
var _continueWriteTempText = "";// 没写完的情况续写的中间结果
var preType = "";//上一次的按钮名称
var ctype = ""; //当前的类型
var _stopflag = false;
var zl_select_list = undefined ;// 摘录的列表
var _stopflag = false;

let tenantCode = undefined;
let loginId = undefined;

let _checkboxAll = false;
let wps_ = undefined;

// 打开软件的时间
let startTime = "";
let endTime = "";

// 是否使用深度思考
let useDeepThinking = undefined;

let onlinesearch = false;
let onlinefinesearch  = false;

let zskz = {
    'actnum': 2,  // 0 1 2 3 按钮的顺序
    'zs': 2000,  //激活的字数
}


let GWBD = {
    left:'',
    right:'',
}



// 参考内容的id

let FXCK = {
    fxnr_id: '',
    fxnr_txt: '',
    fxnr_title: '',
}

let HewlffirstRow = [];
let WhldnfirstRow = [];

let _scrollToBottom = true;

// 删除 思考标签的正则
const THINKREG = /<\/?think>/g;

// 添加内容参考, 收藏，摘录，录音
let TJNRCK = {
    sc: [],
    zl: [],
    ly: [],
};

// 添加结构参考
let TJJGCK = {
    tplContent: ''// 仿写材料模版内容
};

// 起草页填写内容存cookie
const QC_COOKIE_KEY_PREFIX = "qc_option_";
let QC_OPTION = {
    gwwz: '',// 公文文种，存控件id，存值的话不好找
    title: '',// 标题
    cause: '',// 关键信息
    zskz: {
        'actnum': 2,  // 0 1 2 3 按钮的顺序
        'zs': 2000,  //激活的字数
    },
    outline: ''// 填写的大纲（非生成的大纲）
};


let evaData = '';


let hmdRagZsk = [0,1,2]
let commonZsk = [0,1,2]

let selectType ;

//日期格式化的代码
function formatDate(date, format) {
    const map = {
        'M': date.getMonth() + 1, // 月份
        'd': date.getDate(), // 日
        'h': date.getHours(), // 小时
        'm': date.getMinutes(), // 分钟
        's': date.getSeconds(), // 秒
        'q': Math.floor((date.getMonth() + 3) / 3), // 季度
        'S': date.getMilliseconds() // 毫秒
    };

    format = format.replace(/([yMdhmsqS])+/g, (all, t) => {
        let v = map[t];
        if (v !== undefined) {
            if (all.length > 1) {
                v = '0' + v;
                v = v.substr(v.length - all.length);
            }
            return v;
        } else if (t === 'y') {
            return (date.getFullYear() + '').substr(4 - all.length);
        }
        return all;
    });
    return format;
}


$(document).ready(function () {
    // 试用版：隐藏“参考信息列表”
    if (window.parent.isSy && Artery.get("atyContainerKgkrp")) {
        Artery.get("atyContainerKgkrp").hide();
    }
    // 适配高分屏，设置初始缩放
    initZoom();
    // 监听单击事件隐藏父页面的字体大小滑块
    document.body.onclick = function (e) {
        if (window.parent.hideFontsize) {
            window.parent.hideFontsize()
        }
        //隐藏排版按钮下拉
        document.getElementById("atyContainerQnbfd").style.display = "none";
        document.getElementById("atyContainerQnbfd1").style.display = "none";
        document.getElementById("atyContainerQnbfd2").style.display = "none";
        document.getElementById("atyContainerPthbv").style.display = "none";
        // 隐藏知识搜索配置框
        if(e.target.id !== 'atyContainerZsksou' && $(e.target).parents('#atyContainerZsksou').length === 0 && e.target.id !== 'atyIconKrivz1' && $(e.target).parents('#atyIconKrivz1').length === 0) {
            $("#atyContainerZsksou").hide();
        }
        // 隐藏添加内容参考-选择文稿弹出层
        if(e.target.id !== 'atyContainerQpucs' && $(e.target).parents('#atyContainerQpucs').length === 0 && e.target.id !== 'atyContainerKervu' && $(e.target).parents('#atyContainerKervu').length === 0) {
            if(Artery.get('atyContainerQpucs')) Artery.get('atyContainerQpucs').hide();
        }
        if(e.target.id !== 'atyContainerQpucshmd' && $(e.target).parents('#atyContainerQpucshmd').length === 0 && e.target.id !== 'atyContainerKervuhmd' && $(e.target).parents('#atyContainerKervuhmd').length === 0) {
            if(Artery.get('atyContainerQpucshmd')) Artery.get('atyContainerQpucshmd').hide();
        }
        // 隐藏添加结构参考-选择文稿弹出层
        if(e.target.id !== 'atyContainerGengj' && $(e.target).parents('#atyContainerGengj').length === 0 && e.target.id !== 'atyContainerYonzk' && $(e.target).parents('#atyContainerYonzk').length === 0) {
            if(Artery.get('atyContainerGengj')) Artery.get('atyContainerGengj').hide();
        }
        // 隐藏添加结构参考-选择模版弹出层
        if(e.target.id !== 'atyContainerAahle' && $(e.target).parents('#atyContainerAahle').length === 0 && e.target.id !== 'atyContainerVdsfq' && $(e.target).parents('#atyContainerVdsfq').length === 0) {
            if(Artery.get('atyContainerAahle')) Artery.get('atyContainerAahle').hide();
        }
    }
    // 监听生成全文滚动事件
    document.getElementById('atyRegionCenterImgov').onscroll = function (e) {
        if(e.target.scrollTop + e.target.offsetHeight + 1 >= e.target.scrollHeight) {
            _scrollToBottom = true;
        }else {
            _scrollToBottom = false;
        }
    }
    // 监听摘要结果滚动事件
    document.getElementById('atyRegionCenter_14e7b').onscroll = function (e) {
        if(e.target.scrollTop + e.target.offsetHeight + 1 >= e.target.scrollHeight) {
            _scrollToBottom = true;
        }else {
            _scrollToBottom = false;
        }
    }
    // 监听大纲结果滚动事件
    document.getElementById('atyRegionCenter_15e7b').onscroll = function (e) {
        if(e.target.scrollTop + e.target.offsetHeight + 1 >= e.target.scrollHeight) {
            _scrollToBottom = true;
        }else {
            _scrollToBottom = false;
        }
    }
    // 监听排版按钮事件
    listenPbBtnEvent();
    listenPbBtnEvent1();
    listenPbBtnEvent2();
    // 监听文本区域回车事件
    // document.getElementById("atyTextareaEthmy").addEventListener("keydown", function (event) {
    //     if(event.keyCode === 13 && event.altKey == true) {
    //         event.preventDefault();
    //         Artery.get("atyTextareaEthmy").setValue(Artery.get("atyTextareaEthmy").getValue()+"\n");
    //         Artery.get("atyTextareaEthmy").focus();
    //     }else if(event.keyCode === 13) {
    //         event.preventDefault();
    //         _continueWriteTime = 0;// 回车的时候重置下
    //         atyIconQyibzOnClickClient(null, false);
    //     }
    // });
    wpsinner = window.parent.wpsinner;
    // 初始化当前页面是否web版本嵌入
    isWeb = window.parent.isWeb;
    if(wpsinner) {
        document.getElementById("atyContainerPkudo").style.display = 'none';// 信创下隐藏排版按钮
        document.getElementById("atyContainerPkudo1").style.display = 'none';// 信创下隐藏排版按钮
        document.getElementById("atyContainerPkudo2").style.display = 'none';// 信创下隐藏排版按钮
        document.getElementById("atyButtonWebPb1").style.display = 'none';// 信创下隐藏网页版排版按钮
        document.getElementById("atyButtonWebPb2").style.display = 'none';// 信创下隐藏网页版排版按钮
        document.getElementById("atyButtonWebPb3").style.display = 'none';// 信创下隐藏网页版排版按钮
        if(Artery.get("atyButtonLmier1")) {
            Artery.get("atyButtonLmier1").show();// 信创下显示上传按钮
        }
        if(Artery.get("atyButtonLmier2")) {
            Artery.get("atyButtonLmier2").show();// 信创下显示上传按钮
        }
        Artery.get("atyUploadAejkv1").hide();// 信创下显示上传按钮
        Artery.get("atyUploadAejkv2").hide();// 信创下显示上传按钮
        // document.body.style.minWidth = '370px';
        wps_ = window.parent.wps === undefined? window.parent.parent.wps : window.parent.wps;
        // 添加内容参考-上传文件
        Artery.get('atyContainerXahpf').hide();
        Artery.get('atyContainerDiett').show();
        // 添加结构参考-上传文件
        Artery.get('atyContainerTtaod').hide();
        Artery.get('atyContainerMbmat').show();
    } else if(isWeb) {
        document.getElementById("atyContainerPkudo").style.display = 'none';// 隐藏排版按钮
        document.getElementById("atyContainerPkudo1").style.display = 'none';// 隐藏排版按钮
        document.getElementById("atyContainerPkudo2").style.display = 'none';// 隐藏排版按钮
        if(Artery.get("atyUploadAejkv1")){
            Artery.get("atyUploadAejkv1").show();// 非信创下显示上传控件
        }
        if(Artery.get("atyUploadAejkv2")){
            Artery.get("atyUploadAejkv2").show();// 非信创下显示上传控件
        }
        if(Artery.get("atyUploadAejkv2hmd")){
            Artery.get("atyUploadAejkv2hmd").show();// 非信创下显示上传控件
        }
        // 添加内容参考-上传文件
        if(Artery.get('atyContainerXahpf')){
            Artery.get('atyContainerXahpf').show();
        }
        if(Artery.get('atyContainerXahpfhmd')){
            Artery.get('atyContainerXahpfhmd').show();
        }
        if(Artery.get('atyContainerDiett')){
            Artery.get('atyContainerDiett').hide();
        }
        if(Artery.get('atyContainerDietthmd')){
            Artery.get('atyContainerDietthmd').hide();
        }
        // 添加结构参考-上传文件
        if(Artery.get('atyContainerTtaod')){
            Artery.get('atyContainerTtaod').show();
        }
        if(Artery.get('atyContainerMbmat')){
            Artery.get('atyContainerMbmat').hide();
        }
    }else {
        Artery.get("atyUploadAejkv1").show();// 非信创下显示上传控件
        Artery.get("atyUploadAejkv2").show();// 非信创下显示上传控件
        // document.body.style.minWidth = '440px';
        document.getElementById("atyButtonWebPb1").style.display = 'none';// 隐藏网页版排版按钮
        document.getElementById("atyButtonWebPb2").style.display = 'none';// 隐藏网页版排版按钮
        document.getElementById("atyButtonWebPb3").style.display = 'none';// 隐藏网页版排版按钮
        // 添加内容参考-上传文件
        Artery.get('atyContainerXahpf').show();
        Artery.get('atyContainerDiett').hide();
        // 添加结构参考-上传文件
        Artery.get('atyContainerTtaod').show();
        Artery.get('atyContainerMbmat').hide();
    }

    if(Artery.getParentArtery() && 'uCp' in Artery.getParentArtery().params) {
        loginId = Artery.getParentArtery().params.uCp;
    }
    if(Artery.getParentArtery() &&'tenantCode' in Artery.getParentArtery().params) {
        tenantCode = Artery.getParentArtery().params.tenantCode;
    }

    //初始化打开的时间
    var today = new Date(); // 获取今天的日期
    var yesterday = new Date(today); // 通过复制今天的日期创建一个新的日期对象
    yesterday.setDate(today.getDate() - 1); // 将新isRequesting日期对象的日期减去一天
    startTime = formatDate(yesterday, 'yyyy-MM-dd');

    // 如果有知识库检索，默认为true
    if(Artery.get('atyContainerEtrak')) {
        onlinesearch = true;
    }

    // 绑定输入框回车事件
    bindInputEnterEvent();

    // 读取cookie初始化上次起草时填写的内容
    readCookieAndSet();

    // 读取音频数据
    if(Artery.params.audioId && Artery.params.name) {
        // 开始处理数据， （1）切换到事务文书，选中会议纪要，（2）填写标题， 选中参考信息， 生成一个内容要求模板
        // 取后台请求一下数据
        Artery.request({
            url: 'client/fdqc/audioInfo',
            async: false,
            data:{
                id: Artery.params.audioId
            },
            success: function (data, textStatus, response, cfg) {
                let title = data.title;
                let time = data.time;
                atyRadioOkcxnOnChangeClient('', 'other', '');
                Artery.get('atyRadioOkcxn').setValue('other');
                Artery.get('atyContainerXchua').click();
                Artery.get('atyInputYkjao').setValue(title);
                Artery.get('atyTextareaPelma').setValue("会议时间:" + time+ "\n会议人员:");
                // 写参考文件
                flashPadWarning('atyTextareaPelma');
                addLyItem(Artery.params.audioId, title)
            },
            error: function (response, textStatus, errorThrown, options) {
                console.log(response)
                console.log("获取状态失败返回false");
            }
        })
    }
})
function flashPadWarning(id) {
    var el = document.getElementById(id);
    if (!el) return;
    el.classList.add('flash-warning');
    setTimeout(function() {
        el.classList.remove('flash-warning');
    }, 2500); // 0.5s * 3次
}
function addLyItem(id, name){
    // 生成文件列表
    TJNRCK.ly.push({id: id, text: name});
    var div = document.createElement("div");
    div.className = "container-file";
    div.id = id;
    var icon_file = document.createElement("i");
    icon_file.className = "icon-luyin";
    var span = document.createElement("span");
    span.className = "text-filename";
    span.innerText = name;
    span.title = name;
    var icon_del = document.createElement("i");
    icon_del.className = "icon-file-del";
    icon_del.onclick = function () {
        document.getElementById("atyContainerEdvxu").removeChild(div);
        TJNRCK.ly.splice(TJNRCK.ly.indexOf({id: id, text: name}), 1);
    }
    var icon_loading = document.createElement("i");
    icon_loading.className = "icon-file-loading";
    icon_loading.id = "icon-file-loading";
    div.append(icon_file);
    div.append(span);
    div.append(icon_del);
    div.append(icon_loading);
    document.getElementById("atyContainerEdvxu").append(div);
}


function bindInputEnterEvent() {
    var e = document.getElementById('atyInputGowub');
    if(e) {
        e.addEventListener("keydown", function (event) {
            if(event.keyCode === 13) {
                atyInputGowubOnIconClickClient();
            }
        });
    }

    var e2 = document.getElementById('atysearchInput');
    if(e2) {
        e2.addEventListener("keydown", function (event) {
            if(event.keyCode === 13) {
                handleSearch();
            }
        });
    }
}

function hideLoadingAndDone(){
    closeLoadingButtonGroup();
    closeDoneButtonGroup();
}

function listenPbBtnEvent2() {
    document.getElementById("atyContainerPkudo2").onclick = function (e) {
        if(selectedGwwzId.length === 0) {
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        let title = Artery.get(selectedGwwzId).el.text();
        if(title === "工作报告" || title === "调研报告" || title === "日程议程" || title === "活动策划" || title === "学习体会"
            || title === "讲话稿" || title === "制度" || title === "其他"){
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        if(!wpsinner && !isWeb) {
            window.parent.aiWriter.formatMainText(title);
            hideLoadingAndDone();
        }
    }
    document.getElementById("atyContainerTjjae2").onclick = function (e) {
        document.getElementById("atyContainerQnbfd2").style.display = "block";
        e.stopPropagation();
    }
    document.getElementById("atyContainerQnbfd2").onclick = function (e) {
        document.getElementById("atyContainerQnbfd2").style.display = "none";
        e.stopPropagation();
        if(selectedGwwzId.length === 0) {
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        let title = Artery.get(selectedGwwzId).el.text();
        if(title === "工作报告" || title === "调研报告" || title === "日程议程" || title === "活动策划" || title === "学习体会"
            || title === "讲话稿" || title === "制度" || title === "其他"){
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        hideLoadingAndDone();
        if(!wpsinner && !isWeb) {
            window.parent.aiWriter.format(title);
        }
    }
}


function listenPbBtnEvent1() {
    document.getElementById("atyContainerPkudo1").onclick = function (e) {
        if(selectedGwwzId.length === 0) {
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        let title = Artery.get(selectedGwwzId).el.text();
        if(title === "工作报告" || title === "调研报告" || title === "日程议程" || title === "活动策划" || title === "学习体会"
            || title === "讲话稿" || title === "制度" || title === "其他"){
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        if(!wpsinner && !isWeb) {
            window.parent.aiWriter.formatMainText(title);
            hideLoadingAndDone();
        }
    }
    document.getElementById("atyContainerTjjae1").onclick = function (e) {
        document.getElementById("atyContainerQnbfd1").style.display = "block";
        e.stopPropagation();
    }
    document.getElementById("atyContainerQnbfd1").onclick = function (e) {
        document.getElementById("atyContainerQnbfd1").style.display = "none";
        e.stopPropagation();
        if(selectedGwwzId.length === 0) {
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        let title = Artery.get(selectedGwwzId).el.text();
        if(title === "工作报告" || title === "调研报告" || title === "日程议程" || title === "活动策划" || title === "学习体会"
            || title === "讲话稿" || title === "制度" || title === "其他"){
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        hideLoadingAndDone();
        if(!wpsinner && !isWeb) {
            window.parent.aiWriter.format(title);
        }
    }
}

function listenPbBtnEvent() {
    document.getElementById("atyContainerPkudo").onclick = function (e) {
        if(selectedGwwzId.length === 0) {
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        let title = Artery.get(selectedGwwzId).el.text();
        if(title === "工作报告" || title === "调研报告" || title === "日程议程" || title === "活动策划" || title === "学习体会"
            || title === "讲话稿" || title === "制度" || title === "其他"){
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        if(!wpsinner && !isWeb) {
            window.parent.aiWriter.formatMainText(title);
            hideLoadingAndDone();
        }
    }
    document.getElementById("atyContainerTjjae").onclick = function (e) {
        document.getElementById("atyContainerQnbfd").style.display = "block";
        e.stopPropagation();
    }
    document.getElementById("atyContainerQnbfd").onclick = function (e) {
        document.getElementById("atyContainerQnbfd").style.display = "none";
        e.stopPropagation();
        if(selectedGwwzId.length === 0) {
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        let title = Artery.get(selectedGwwzId).el.text();
        if(title === "工作报告" || title === "调研报告" || title === "日程议程" || title === "活动策划" || title === "学习体会"
            || title === "讲话稿" || title === "制度" || title === "其他"){
            Artery.alert.warning("请选择法定公文种类");
            return;
        }
        hideLoadingAndDone();
        if(!wpsinner && !isWeb) {
            window.parent.aiWriter.format(title);
        }
    }
}


function gscontain(name){
    var keys = Object.keys(_xcfiles);
    for(var i = 0; i < keys.length; i++){
        if(keys[i].startsWith(name)){
            return true
        }
    }
    return false
}

/**
 * 点击时脚本(atyButtonLmier)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonLmier1OnClickClient(rc) {
    if(FXCK.fxnr_id !== ""){
        Artery.alert.warning('已经有参考内容')
        return
    }
    var dialog = wps_.Application.FileDialog(3);
    dialog.Filters.Add("doc文档和docx文档和wps文档和txt文档和pdf文档", "*.doc;*.docx;*.wps;*.txt;*.pdf", 1);
    dialog.AllowMultiSelect = false;//单选，true为多选
    if(dialog.Show()) {
        dialog.Execute();
        var duplicateName = false;
        for(var i = 1; i <= dialog.SelectedItems.Count; i++) {
            // 判断超出文件数量限制
            if(Object.keys(_files).length + Object.keys(_xcfiles).length === 10) {
                Artery.alert.warning("只可上传一个仿写格式文件");
                return;
            }
            var filepath = dialog.SelectedItems.Item(i);
            var filename = filepath.substring(filepath.lastIndexOf("/")+1);
            if("ckgs_" + filename in _xcfiles || gscontain("ckgs")) {
                duplicateName = true;
            }else {
                _xcfiles["ckgs_" + filename] = filepath;

                var div = document.createElement("div");
                div.className = "container-file";
                div.id = getUuid();

                var icon_file = document.createElement("i");
                icon_file.className = "icon-file";

                var span = document.createElement("span");
                span.className = "text-filename";
                span.innerText = filename;
                span.title = filename;

                var icon_del = document.createElement("i");
                icon_del.className = "icon-file-del";
                icon_del.setAttribute("data-filename", filename);
                icon_del.onclick = function (e) {
                    var deldiv = e.target.parentNode;
                    var delname = e.target.dataset.filename;
                    document.getElementById("atyContainerJslmk").removeChild(deldiv);
                    delete _xcfiles["ckgs_" + delname];
                }

                div.append(icon_file);
                div.append(span);
                div.append(icon_del);
                document.getElementById("atyContainerJslmk").append(div);
            }
        }
        if(duplicateName) {
            Artery.alert.warning("只可上传一个仿写格式文件");
        }
    }
}


/**
 * 点击时脚本(atyButtonLmier)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonLmier2OnClickClient(rc) {
    var dialog = wps_.Application.FileDialog(3);
    dialog.Filters.Add("doc文档和docx文档和wps文档和txt文档和pdf文档", "*.doc;*.docx;*.wps;*.txt;*.pdf", 1);
    dialog.AllowMultiSelect = true;//单选，true为多选
    if(dialog.Show()) {
        dialog.Execute();
        var duplicateName = false;
        for(var i = 1; i <= dialog.SelectedItems.Count; i++) {
            // 判断超出文件数量限制
            if(Object.keys(_files).length + Object.keys(_xcfiles).length === 10) {
                Artery.alert.warning("文件个数超出最大限制");
                return;
            }
            var filepath = dialog.SelectedItems.Item(i);
            var filename = filepath.substring(filepath.lastIndexOf("/")+1);
            if("cknr_" + filename in _xcfiles) {
                duplicateName = true;
            }else {
                _xcfiles["cknr_" + filename] = filepath;

                var div = document.createElement("div");
                div.className = "container-file";
                div.id = getUuid();

                var icon_file = document.createElement("i");
                icon_file.className = "icon-file";

                var span = document.createElement("span");
                span.className = "text-filename";
                span.innerText = filename;
                span.title = filename;

                var icon_del = document.createElement("i");
                icon_del.className = "icon-file-del";
                icon_del.setAttribute("data-filename", filename);
                icon_del.onclick = function (e) {
                    var deldiv = e.target.parentNode;
                    var delname = e.target.dataset.filename;
                    document.getElementById("atyContainerEdvxu").removeChild(deldiv);
                    delete _xcfiles["cknr_" + delname];
                }

                div.append(icon_file);
                div.append(span);
                div.append(icon_del);
                document.getElementById("atyContainerEdvxu").append(div);
            }
        }
        if(duplicateName) {
            Artery.alert.warning("不可上传同名文件");
        }
    }
}

/**
 * @description 适配高分屏设置缩放
 */
function initZoom() {
    if(Artery.getParentArtery()){
        if('fontZoom' in Artery.getParentArtery().params) {
            document.body.style.zoom = parseFloat(Artery.getParentArtery().params.fontZoom);
        }else {
            if(window.screen.width >= 2560) {
                document.body.style.zoom = 1.5;
            }else {
                document.body.style.zoom = 1;
            }
        }
    }
}

/**
 * 值改变时脚本(atyRadioOkcxn)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  newValue 控件改变后的新值
 * @param  oldValue 控件改变前的旧值
 */
function atyRadioOkcxnOnChangeClient (rc, newValue, oldValue){
    if(newValue === 'gov') {
        Artery.get('atyToggleLayoutPymgu').show();// 公文种类
        Artery.get('atyToggleLayoutHrdhc').hide();// 公文种类
        handleWindowSize()
    }else {
        Artery.get('atyToggleLayoutPymgu').hide();// 公文种类
        Artery.get('atyToggleLayoutHrdhc').show();// 公文种类
        handleWindowSize()
    }
    //updateWzcdState();
}

/**
 * 选中时脚本(atyToggleLayoutPymgu)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  selectedItem 被选中的子控件对象
 */
function atyToggleLayoutPymguOnSelectClient (rc,selectedItem){
    if(selectedGwwzId.length > 0) {
        document.getElementById(selectedGwwzId).classList.remove("selected");
    }
    if(selectedGwwzId === selectedItem.id) {
        selectedGwwzId = "";
        return;
    }
    document.getElementById(selectedItem.id).classList.add("selected");
    selectedGwwzId = selectedItem.id;

    if(selectedGwwzId !== ''){
        //updateWzcdState();

        var gwwz = selectedGwwzId.length === 0 ? "" : Artery.get(selectedGwwzId).el.text();
        // 清空事由
        //Artery.get("atyTextareaPelma").setValue("");

        rc.put("gwwz", gwwz);
        rc.put("template", Artery.get("atyTextareaPelma").getValue())
        rc.send(function (res) {
            console.log(res);
            var result = res.btType
            var template = res.template
            if(template === ""){
                // Artery.get("atyButtonEpssa").hide();
            }else{
                // Artery.get("atyButtonEpssa").show();
            }

            if(res.needClear){
                if(template !== ""){
                    Artery.get("atyTextareaPelma").setValue("");
                    $('#atyTextareaPelma .aty-input-common').attr("placeholder", template)
                }else{
                    Artery.get("atyTextareaPelma").setValue("");
                    $('#atyTextareaPelma .aty-input-common').attr("placeholder", "填写文章的关键信息，如背景、事项、结论等")
                }
            }
            if(result === "全文"){
                document.getElementById("atyButtonZwltx").classList.remove("aty-button-zsmb");
                document.getElementById("atyButtonZwltx").classList.add("aty-button-default");

                document.getElementById("atyButtonAcqsr").classList.add("aty-button-zsmb");
                document.getElementById("atyButtonAcqsr").classList.remove("aty-button-default");

                document.getElementById("atyButtonYvodk").classList.add("aty-button-zsmb");
                document.getElementById("atyButtonYvodk").classList.remove("aty-button-default");
            }
            if(result === "大纲"){
                document.getElementById("atyButtonAcqsr").classList.remove("aty-button-zsmb");
                document.getElementById("atyButtonAcqsr").classList.add("aty-button-default");

                document.getElementById("atyButtonYvodk").classList.add("aty-button-zsmb");
                document.getElementById("atyButtonYvodk").classList.remove("aty-button-default");

                document.getElementById("atyButtonZwltx").classList.add("aty-button-zsmb");
                document.getElementById("atyButtonZwltx").classList.remove("aty-button-default");
            }
            if(result === "摘要"){
                document.getElementById("atyButtonYvodk").classList.remove("aty-button-zsmb");
                document.getElementById("atyButtonYvodk").classList.add("aty-button-default");

                document.getElementById("atyButtonAcqsr").classList.add("aty-button-zsmb");
                document.getElementById("atyButtonAcqsr").classList.remove("aty-button-default");

                document.getElementById("atyButtonZwltx").classList.add("aty-button-zsmb");
                document.getElementById("atyButtonZwltx").classList.remove("aty-button-default");

            }
        });
    }else{
        Artery.alert.warning("当前未选择公文文种，请选择后重试")
    }
}

/**
 * 点击时脚本(atyButtonEpssa)
 *  填写模板信息
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonEpssaOnClickClient(rc) {
    if(selectedGwwzId !== ''){
        // var gwwz = selectedGwwzId.length === 0 ? "" : Artery.get(selectedGwwzId).subComponents[0].el.text();
        var gwwz = selectedGwwzId.length === 0 ? "" : Artery.get(selectedGwwzId).el.text();
        rc.put("gwwz", gwwz);
        rc.send(function (result) {
            if(result !== ""){
                Artery.get("atyTextareaPelma").setValue(result);
            }
        });
    }else{
        Artery.alert.warning("当前未选择公文文种，请选择后重试")
    }
}

/**
 * 文件提交完成时脚本(atyUploadAejkv)
 *
 * @param  result 提交返回结果
 */
function atyUploadAejkvOnSubmit (result){

}

/**
 * 添加文件后脚本(atyUploadAejkv)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  files 选中的文件对象，类型为File对象数组
 */
function atyUploadAejkv1OnAfterAddClient (rc, files){
    if(Artery.get('atyContainerGengj')) {
        Artery.get('atyContainerGengj').hide();
    }

    var file = files[0];

    if("ckgs_" + file.name in _files && _files["ckgs_" + file.name] === "ckgs") {
        Artery.alert.warning("不可上传同名文件");
        document.getElementById("atyUploadAejkv1_queue").removeChild(document.getElementById(file.id));
        return false;
    }

    _files["ckgs_" + file.name] = "ckgs";

    // 生成文件列表
    var div = document.createElement("div");
    div.className = "container-file";
    div.id = file.id;
    var icon_file = document.createElement("i");
    icon_file.className = "icon-file";
    var span = document.createElement("span");
    span.className = "text-filename";
    span.innerText = file.name;
    span.title = file.name;
    var icon_del = document.createElement("i");
    icon_del.className = "icon-file-del";
    icon_del.onclick = function () {
        document.getElementById("atyContainerJslmk").removeChild(div);
        document.getElementById("atyUploadAejkv1_queue").removeChild(document.getElementById(file.id));
        delete _files["ckgs_" + file.name];
    }
    var icon_loading = document.createElement("i");
    icon_loading.className = "icon-file-loading";
    icon_loading.id = "icon-file-loading";
    div.append(icon_file);
    div.append(span);
    div.append(icon_del);
    div.append(icon_loading);
    document.getElementById("atyContainerJslmk").append(div);
}

/**
 * 添加文件后脚本(atyUploadAejkv)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  files 选中的文件对象，类型为File对象数组
 */
function atyUploadAejkv2OnAfterAddClient (rc, files){
    if(Artery.get('atyContainerQpucs')) {
        Artery.get('atyContainerQpucs').hide();
    }

    var file = files[0];

    if(("cknr_" + file.name) in _files && _files["cknr_" + file.name] === "cknr") {
        Artery.alert.warning("不可上传同名文件");
        document.getElementById("atyUploadAejkv2_queue").removeChild(document.getElementById(file.id));
        return false;
    }

    _files["cknr_" + file.name] = "cknr";

    // 生成文件列表
    var div = document.createElement("div");
    div.className = "container-file";
    div.id = file.id;
    var icon_file = document.createElement("i");
    icon_file.className = "icon-file";
    var span = document.createElement("span");
    span.className = "text-filename";
    span.innerText = file.name;
    span.title = file.name;
    var icon_del = document.createElement("i");
    icon_del.className = "icon-file-del";
    icon_del.onclick = function () {
        document.getElementById("atyContainerEdvxu").removeChild(div);
        document.getElementById("atyUploadAejkv2_queue").removeChild(document.getElementById(file.id));
        delete _files["cknr_" + file.name];
    }
    var icon_loading = document.createElement("i");
    icon_loading.className = "icon-file-loading";
    icon_loading.id = "icon-file-loading";
    div.append(icon_file);
    div.append(span);
    div.append(icon_del);
    div.append(icon_loading);
    document.getElementById("atyContainerEdvxu").append(div);
}


/**
 * 添加文件后脚本(atyUploadAejkv)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  files 选中的文件对象，类型为File对象数组
 */
function atyUploadAejkv2hmdOnAfterAddClient (rc, files){
    if(Artery.get('switchChangeZsss').getValue()){
        return;
    }
    if(Artery.get('atyContainerQpucshmd')) {
        Artery.get('atyContainerQpucshmd').hide();
    }

    var file = files[0];

    if(("cknr_" + file.name) in _files && _files["cknr_" + file.name] === "cknr") {
        Artery.alert.warning("不可上传同名文件");
        document.getElementById("atyUploadAejkv2hmd_queue").removeChild(document.getElementById(file.id));
        return false;
    }

    _files["cknr_" + file.name] = "cknr";

    // 生成文件列表
    var div = document.createElement("div");
    div.className = "container-file";
    div.id = file.id;
    var icon_file = document.createElement("i");
    icon_file.className = "icon-file";
    var span = document.createElement("span");
    span.className = "text-filename";
    span.innerText = file.name;
    span.title = file.name;
    var icon_del = document.createElement("i");
    icon_del.className = "icon-file-del";
    icon_del.onclick = function () {
        document.getElementById("atyContainerEdvxuhmd").removeChild(div);
        document.getElementById("atyUploadAejkv2hmd_queue").removeChild(document.getElementById(file.id));
        delete _files["cknr_" + file.name];
    }
    var icon_loading = document.createElement("i");
    icon_loading.className = "icon-file-loading";
    icon_loading.id = "icon-file-loading";
    div.append(icon_file);
    div.append(span);
    div.append(icon_del);
    div.append(icon_loading);
    document.getElementById("atyContainerEdvxuhmd").append(div);
}


function resetContaine(){
    _continueWriteTime = 0;
    _continueWriteTempText = "";
}
/**
 * 点击时脚本(atyButtonYvodk)
 * 点击生成摘要
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonYvodkOnClickClient(rc) {
    closeDoneButtonGroup()
    if(Artery.get("atyInputYkjao").isValid()){
        pageTurning(2);
        resetContaine();
        generateDocument("摘要-1")
    }
}


/**
 * 点击时脚本(atyButtonAcqsr)
 * 生成大纲信息 page1
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonAcqsrOnClickClient(rc) {
    closeDoneButtonGroup()
    if(Artery.get("atyInputYkjao").isValid()){
        pageTurning(3);
        generateDocument("大纲-1")
    }
}

/**
 * 自定义验证时脚本(atyInputYkjao)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  value 进行校验的值
 */
function atyInputYkjaoOnValidClient (rc, value){
    if(value === ""){
        return "标题不能为空";
    }
    if(value.length < 3 || value.length > 70){
        return "请填写合理的标题"
    }
    return true;
}


/**
 * 点击时脚本(atyButtonZsxrh)
 * 上一步， 返回1
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonZsxrhOnClickClient(rc) {
    closeDoneButtonGroup()
    atyIconXgekiOnClickClient()
    pageTurning(1);
}

/**
 * 点击时脚本(atyButtonUvqir)
 * 生成大纲
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonUvqirOnClickClient(rc) {
    if(requesting) {
        Artery.alert.warning("正在生成请稍候");
        return;
    }
    closeDoneButtonGroup()
    pageTurning(3);
    resetContaine()
    generateDocument("大纲-2")

}

/**
 * 点击时脚本(atyButtonExysd)
 * 返回2
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonExysdOnClickClient(rc) {
    if(requesting) {
        Artery.alert.warning("正在生成请稍候");
        return;
    }
    closeDoneButtonGroup()
    atyIconXgekiOnClickClient()
    if(preType === "大纲-1"){
        pageTurning(1);
    }else if(preType === "大纲-2"){
        pageTurning(2);
    }
}


/**
 * 点击时脚本(atyButtonZwltx)
 * 直接生成全文 1
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonZwltx1OnClickClient(rc) {
    _continueWriteTime = 0;// 重置持续
    closeDoneButtonGroup()
    // 生成全文
    if(Artery.get("atyInputYkjao").isValid()){
        // if(checkCondition("全文-1", Artery.get("atyInputYkjao").getValue())){
        //     pageTurning(3);
        //     generateDocument("大纲-1")
        // }else{
        //     generateDocument("全文-1")
        // }
        selectType = "全文-1";
        containContent("全文-1")
        // generateDocument()
    }
    writeCookie();
}

/**
 * 点击时脚本(atyButtonZbnma)
 * 生成全文 2
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonZbnmaOnClickClient(rc) {
    // 生成全文
    _continueWriteTime = 0;// 重置持续
    closeDoneButtonGroup()
    // if(checkCondition("全文-2", Artery.get("atyInputYkjao").getValue())){
    //     pageTurning(3);
    //     resetContaine()
    //     generateDocument("大纲-2")
    // }else{
    //     generateDocument("全文-2")
    // }
    // generateDocument("全文-2")
    containContent("全文-2")
}


/**
 * 点击时脚本(atyButtonBzajl)
 * 生成全文 3
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonBzajlOnClickClient(rc) {
    // 生成全文
    _continueWriteTime = 0;// 重置持续
    closeDoneButtonGroup()
    // generateDocument("全文-3")
    containContent("全文-3")
    writeCookie();
}

function handleWindowSize() {
    var btn1 = document.getElementById("expandhideBtn1")
    var btn2 = document.getElementById("expandhideBtn2")
    if(btn1.classList.contains("hide-icon")){
        HewlffirstRow = []
        Hewtags =  document.querySelectorAll(".select1")
        Hewtags.forEach(tag => {
            tag.style.display = "";
        });
        if (Hewtags && Hewtags[0]) {
            let HewtagsfirstRowOffsetTop = Hewtags[0].offsetTop;
            Hewtags.forEach(tag => {
                if (tag.offsetTop !== HewtagsfirstRowOffsetTop) {
                    HewlffirstRow.push(tag);
                }
            });
            HewlffirstRow.forEach(tag => {
                tag.style.display = "none";
            });
        }
    }

    if(btn2.classList.contains("hide-icon")){
        WhldnfirstRow = []
        Whltags =  document.querySelectorAll(".select2")
        Whltags.forEach(tag => {
            tag.style.display = "";
        });
        if(Whltags && Whltags[0]){
            let WhltagsfirstRowOffsetTop = Whltags[0].offsetTop;
            Whltags.forEach(tag => {
                if (tag.offsetTop !== WhltagsfirstRowOffsetTop) {
                    WhldnfirstRow.push(tag);
                }
            });
            WhldnfirstRow.forEach(tag => {
                tag.style.display = "none";
            });
        }
    }
}

handleWindowSize();

window.addEventListener("resize",  Artery.debounce(handleWindowSize, this, 100));

window.addEventListener("message", function (event){
    console.log(event)
    if(event.data){
        if(event.data.name === "clearText"){
            if(isWeb){
                window.parent.clearContent();
            }else if(wpsinner){
                app = wps_.WpsApplication();


            }else{
                // window.parent.aiWriter.clean()
                window.parent.aiWriter.selectAllText();
                window.parent.aiWriter.deleteSelectedText();
            }
        }
        generateDocument(event.data.type);
    }
}, false)

/**
 * 点击时脚本(expandhideBtn1)
 * @param  rc 系统提供的AJAX调用对象
 */
function changeExpandBtn1(rc){
    reloadRange(1,HewlffirstRow)
}

/**
 * 点击时脚本(expandhideBtn2)
 * @param  rc 系统提供的AJAX调用对象
 */
function changeExpandBtn2(rc){
    reloadRange(2,WhldnfirstRow)
}

function reloadRange(value, array){
    let tags, btn;
    if(value === 1){
        btn = document.getElementById("expandhideBtn1")
        array = []
        tags =  document.querySelectorAll(".select1")
    } else{
        btn = document.getElementById("expandhideBtn2")
        array = []
        tags =  document.querySelectorAll(".select2")
    }
    let firstRowOffsetTop = tags[0].offsetTop;
    tags.forEach(tag => {
        if (tag.offsetTop !== firstRowOffsetTop) {
            array.push(tag);
        }
    });
    if(btn.classList.contains("hide-icon")){
        array.forEach(tag => {
            tag.style.display = "";
        });
        btn.classList.add("expand-icon");
        btn.classList.remove("hide-icon");
    } else {
        array.forEach(tag => {
            tag.style.display = "none";
        });
        btn.classList.remove("expand-icon");
        btn.classList.add("hide-icon");
    }
}
//定义方法由父页面调用------------------------------------------
/**
 * 清空
 */
window.clear = function () {
    if(requesting) {
        Artery.alert.warning("正在生成请稍后")
        return;
    }
    if(selectedContentTypeId !== 'atyContainerWeepi') {
        Artery.get("atyContainerWeepi").click();
    }
    if(selectedGwwzId.length > 0) {
        document.getElementById(selectedGwwzId).classList.remove("selected");
        selectedGwwzId = "";
    }
    Artery.get("atyCheckboxKlfyz").setValue("refertogov");
    Artery.get("atyRadioYepkf").setValue("content");
    Artery.get("atyTextareaEthmy").setValue("");
    document.getElementById("atyContainerEdvxu").innerHTML = "";// 参考资料文件列表
    document.getElementById("atyContainerJslmk").innerHTML = "";// 仿写材料文件列表
    document.getElementById("atyUploadAejkv_queue").innerHTML = "";
    Artery.get('atyContainerBwohv').hide();
    _files = {};
    _xcfiles = {}
    hideLoadingAndDone();
}


function pageTurning(i){
    if(i === 1){
        Artery.get("atyContainerTqipx").show();
        Artery.get("atyContainerTtiyp").show();
        Artery.get("atyContainerOdkut").hide();
        Artery.get("atyContainerAzfzn").hide();
        Artery.get("atyContainerNtmza").hide();
        Artery.get("atyContainerTcnim").hide();
    }else if(i === 2){
        Artery.get("atyContainerTqipx").hide();
        Artery.get("atyContainerTtiyp").hide();
        Artery.get("atyContainerOdkut").show();
        Artery.get("atyContainerAzfzn").show();
        Artery.get("atyContainerNtmza").hide();
        Artery.get("atyContainerTcnim").hide();
        var height = $('.container-choose').height();
        console.log("gaodu ", height)
        $('.zhaiyao-text-area .aty-input-common').height(height - 150);

    }else if(i === 3){
        Artery.get("atyContainerTqipx").hide();
        Artery.get("atyContainerTtiyp").hide();
        Artery.get("atyContainerOdkut").hide();
        Artery.get("atyContainerAzfzn").hide();
        Artery.get("atyContainerNtmza").show();
        Artery.get("atyContainerTcnim").show();
        var height = $('.container-choose').height();
        console.log("gaodu ", height)
        $('.zhaiyao-text-area .aty-input-common').height(height - 150);
    }

}

/**
 * 点击时触发客户端脚本(atyButtonYvofh)
 * 返回首页
 * @param rc 系统提供的AJAX调用对象
 */
function atyButtonYvofhOnClickClient(rc){
    closeDoneButtonGroup()
    atyIconXgekiOnClickClient()
    pageTurning(1);
}


/**
 * 判断是否生成大纲
 */
function checkCondition(type, title) {
    if ((type === '全文-1' || type === '全文-2') && title.endsWith('报告')) {
        return true;
    }
    return false;
}


function splitThinkTag(str){
    var res = []
    const result = str.split("</think>");

    for (let i = 0; i < result.length; i++) {
        if(i === result.length - 1){
            res.push(result[i]);
        }else{
            res.push(result[i] + "</think>");
        }
    }
    return res;
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

function _getZlIds() {
    if(TJNRCK.zl && TJNRCK.zl.length > 0) {
        var ids = [];
        TJNRCK.zl.forEach(function (value) {
            ids.push(value.id);
        });
        return ids.join(',');
    }
    return '';
}
function _getScIds() {
    if(TJNRCK.sc && TJNRCK.sc.length > 0) {
        var ids = [];
        TJNRCK.sc.forEach(function (value) {
            ids.push(value.id);
        });
        return ids.join(',');
    }
    return '';
}

function _getLyIds(){
    if(TJNRCK.ly && TJNRCK.ly.length > 0) {
        var ids = [];
        TJNRCK.ly.forEach(function (value) {
            ids.push(value.id);
        });
        return ids.join(',');
    }
    return '';
}


function containContent(type){
    if(isWeb){
        let text = window.parent.getEditorContentText()
        if(text !== undefined && text !== ""){
            window.parent.showTextModel(type);
        }else{
            generateDocument(type);
        }
    }else if(wpsinner){
        var app = wps_.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc){
            let text = doc.Range(0, doc.Content.End).Text;
            if(text !== undefined && text !== "" && text.trim() !== "") {
                var result = confirm("当前内容区域有内容，默认会追加内容到光标处。是否清空内容后开始写作");
                if (result) {
                    var app = wps_.WpsApplication();
                    var doc = app.ActiveDocument;
                    if (doc) {
                        doc.Range(0, doc.Content.End).Delete()
                        generateDocument(type);
                    }
                } else {
                    generateDocument(type);
                }
            }else{
                generateDocument(type);
            }
        }
    }else{
        generateDocument(type);
    }
}

/**
 * xman:将表格块转换为 HTML 格式
 * @param {string} text 
 * @returns {string} HTML 格式的表格
<table style="border-collapse: collapse; width: 86.7404%; height: 108px;" border="1">
<colgroup><col style="width: 33.3271%;"><col style="width: 33.3271%;"><col style="width: 33.3271%;"></colgroup>
<tbody>
<tr>
<td>测试</td>
<td>测试</td>
<td>&nbsp;</td>
</tr>
<tr>
<td>&nbsp;</td>
<td>&nbsp;</td>
<td>&nbsp;</td>
</tr>
<tr>
<td>&nbsp;</td>
<td>&nbsp;</td>
<td>&nbsp;</td>
</tr>
</tbody>
</table>
 */
function tableBlockToHtml(text) {
    const tableBlockRegex = /<<TABLE>>([\s\S]*?)<<END_TABLE>>/g;
  
    return text.replace(tableBlockRegex, (_, tableContent) => {
      // 删除 --|-- 这样的行
      tableContent = tableContent.replace(/\|?-+\|-+\|?/g, '');

      // console.log("表格文本：" + tableContent);

      const lines = tableContent
        .split("\n")
        .map(l => l.trim())
        .filter(Boolean);
  
      if (!lines.length) {
        return "";
      }
  
      // 表头：第一行
      const columns = lines[0]
        .split("|")
        .map(col => col.trim());
  
      const colCount = columns.length;
      if (colCount === 0) {
        return "";
      }
  
      const colWidth = (100 / colCount).toFixed(4) + "%";
  
      // ===== 解析数据行（从第二行开始）=====
      const rows = lines.slice(1).map(line =>
        line.split("|").map(cell => cell.trim())
      );
  
      // ===== 构建 HTML =====
      let html = `<table style="border-collapse: collapse; width: 100%;" border="1">\n`;
  
      // colgroup
      html += `<colgroup>`;
      for (let i = 0; i < colCount; i++) {
        html += `<col style="width: ${colWidth};">`;
      }
      html += `</colgroup>\n`;
  
      html += `<tbody>\n`;
  
      // 表头行（放在 tbody 第一行）
      html += `<tr>\n`;
      columns.forEach(col => {
        html += `  <td>${escapeHtml(col) || "&nbsp;"}</td>\n`;
      });
      html += `</tr>\n`;
  
      // 数据行
      rows.forEach(row => {
        html += `<tr>\n`;
        for (let i = 0; i < colCount; i++) {
          const cell = row[i] || "";
          html += `  <td>${cell ? escapeHtml(cell) : "&nbsp;"}</td>\n`;
        }
        html += `</tr>\n`;
      });
  
      html += `</tbody>\n</table>`;

      console.log("表格HTML：" + html);
  
      return html;
    });
}

// 防止 XSS / HTML 破坏
function escapeHtml(str) {
    if (str.startsWith('{')) str = str.slice(1);
    if (str.endsWith('}')) str = str.slice(0, -1);

    return str
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
}

/**
 * xman:插入表格到 wps
 * @param {Object} doc wps 文档对象
 * @param {string} text 表格文本
 * @param {Object} range 光标位置
 */
function insertWpsTable(doc, text, range) {
    const tableBlockRegex = /<<TABLE>>([\s\S]*?)<<END_TABLE>>/;
    const match = text.replace(/\|?-+\|-+\|?/g, '').match(tableBlockRegex);

    console.log("表格文本：" + text);
  
    if (!match) {
        return;
    }

    // ===== 解析表格文本 =====
    const lines = match[1]
      .split("\n")
      .map(l => l.trim())
      .filter(Boolean);
  
    if (!lines.length) {
        return;
    }
  
    // 表头：第一行
    const headers = lines[0]
      .split("|")
      .map(h => removeBrace(h.trim()));

    // Artery.alert.warning("表格头：" + headers);
  
    const colCount = headers.length;
    const rowCount = lines.length; // 含表头
  
    // 数据行
    const dataRows = lines.slice(1).map(line =>
      line.split("|").map(cell => cell.trim())
    );

    // Artery.alert.warning("表格数据行：" + rowCount + " 列数：" + colCount);
    
    // ===== 插入表格 =====
    const table = doc.Tables.Add(range, rowCount, colCount);
    
    // ===== 填充表头 =====
    for (let c = 0; c < colCount; c++) {
      table.Cell(1, c + 1).Range.Text = headers[c];
    }
  
    // ===== 填充数据行 =====
    for (let r = 0; r < dataRows.length; r++) {
      for (let c = 0; c < colCount; c++) {
        table.Cell(r + 2, c + 1).Range.InsertAfter(removeBrace(dataRows[r][c]) || "");
      }
    }
  
    // ===== 可选：基础样式 =====
    table.Borders.Enable = 1;
    table.Range.ParagraphFormat.Alignment = 0; // 左对齐
  
    // 插入完成后将光标移到表格后
    doc.Application.Selection.MoveDown();
}

/**
 * 删除字符串两端的括号
 * @param {string} str 
 * @returns {string}
 */
function removeBrace(str) {
    if (str.startsWith('{')) str = str.slice(1);
    if (str.endsWith('}')) str = str.slice(0, -1);
    return str;
}

function generateDocument(type, ex, ischangeOutline){
    // console.log("Xman Invoke generateDocument method with parameters:", type, ex, ischangeOutline);
    // Artery.alert.warning("正在生成请稍候 xman");

    if(type !== "重写"){
        evaData = "";
    }else{
        if(isWeb){

        }
        type = "全文-1"
    }
    if(_stopflag){
        _stopflag = false;
    }
    if(requesting) {
        Artery.alert.warning("正在生成请稍候");
        return;
    }
    var t = type.indexOf("大纲") > -1?  3 : 2;
    if(type === "全文-1"){t = 1;}
    if(type === "全文-2"){t = 2;}
    if(type === "全文-3"){t = 3;}
    var gwwz = selectedGwwzId.length === 0 ? "" : Artery.get(selectedGwwzId).el.text();   //公文文种
    var title = Artery.get("atyInputYkjao").getValue();         // 标题
    var cause = Artery.get("atyTextareaPelma").getValue();      // 事由
    var zhailuids = _getZlIds();//Artery.get("atyCheckboxDebgu").getValue();
    var shoucangids = _getScIds();//Artery.get("atyCheckboxDebgu1").getValue();
    var luyinids = _getLyIds();
    var isUseDwxx = Artery.get("atyCheckboxshshs").getValue() === "yes";
    if(title === "" || title=== undefined){
        Artery.alert.warning("正在生成请稍候");
        return ;
    }
    ctype = type;
    uploadFiles(function (result) {
        if(result!== undefined && result.length > 0){
            _files = []
        }
        if(result !== undefined && result .length > 0){
            $('.icon-file-loading').hide()
            for(var i = 0; i < result.length; i++) {
                _files[result[i].type + '_' + result[i].name] = result[i].path;
            }
        }
        //
        var filepaths = getFilePaths();
        showLoading(t);

        var responseText = "";
        var app = null;
        var start = 0;
        var end = 0;
        var selectText = "";
        var tableText = "";

        /*
        if(wpsinner) {
            app = wps_.WpsApplication();
            start = app.Selection.Start;
            end = app.Selection.End;
            if(start !== end) {
                selectText = app.Selection.Text;
                app.ActiveDocument.Range(app.Selection.Start, app.Selection.End).Delete();
                start = app.Selection.Start;
                end = app.Selection.End;
            }
        }
        */

        requesting = true;

        var abstract = "";
        var outline = "";
        var exchange = false;
        if(ex !== undefined){
            exchange = ex;
        }

        if(type === "大纲-2" || type === "全文-2"){
            abstract = Artery.get('atyTextareaByqgw').getValue();
        }
        if(type === "全文-3"){
            abstract = Artery.get('atyTextareaByqgw').getValue();
            outline = Artery.get('atyTextareaJhkli').getValue();
        }
        if(type === "大纲-1"){
            abstract = "";
            Artery.get('atyTextareaByqgw').setValue("");
        }
        if(type === "全文-2"){
            outline = "";
            Artery.get('atyTextareaJhkli').setValue("");
        }
        if(type.indexOf("大纲") > -1 && !ischangeOutline){
            preType = type;
        }
        var statrTime = new Date().getTime();
        var costTime = -1;
        var llm_mode = getUseDeepSeekStatus()
        if(!llm_mode){
            Artery.get('atyContainerDker').hide();
            Artery.get('atyContainerAbsThink').hide();
            Artery.get('atyContainerOutlineThink').hide();
        }
        if(!onlinesearch) {
            Artery.get('atyContainerSiomi').hide();
            Artery.get('atyContainerFgcli').hide();
            Artery.get('atyContainerRcwsq').hide();
        }
        Artery.get('atyContainerOutlineThinkout').hide();
        Artery.get('atyContainerAbsThinkabs').hide();
        var lastPosition = 0;
        //情况思考的内容
        if(type.indexOf("摘要") > -1){
            Artery.get('atyTextareaByqgw').setValue("");
            Artery.get('atyTextAbsDesc').setText("深度思考中");
            Artery.get('atyTextLoading1').show();
            Artery.get('atytextThinkAbsContent').setText('')
        }
        if(type.indexOf("全文-1") > -1){
            Artery.get('atytextThinkContent').setText("");
            Artery.get('atyTextDesc').setText("深度思考中");
            Artery.get('atyTextLoading').show();
        }
        if(type.indexOf("全文-3") > -1){
            Artery.get('atytextThinkOutContentout').setText("");
            Artery.get('atyTextOutDescout').setText("深度思考中");
            Artery.get('atyTextLoading2out').show();
        }
        if(type.indexOf("全文-2") > -1){
            Artery.get('atytextThinkAbsContentabs').setText("");
            Artery.get('atyTextAbsDescabs').setText("深度思考中");
            Artery.get('atyTextLoading1abs').show();
        }
        if(type.indexOf("大纲") > -1){
            Artery.get('atytextThinkOutContent').setText("");
            Artery.get('atyTextareaJhkli').setValue("");
            Artery.get('atyTextOutDesc').setText("深度思考中");
            Artery.get('atyTextLoading2').show();
        }
        // 查询dify知识库
        searchDifyZsk(type, title, cause,  function (onlinesearchDataId) {
            var cache = '';
            request(type,  gwwz, title, cause, zhailuids, shoucangids, luyinids, filepaths, abstract, outline, exchange, isUseDwxx, zskz.zs, FXCK, llm_mode, onlinesearchDataId, function (request) {
                if(request.responseText.length > 0 && !request.responseText.startsWith('权益错误：')) {
                    if(request.responseText.startsWith('标题：')) {
                        request.responseText = request.responseText.replace('标题：', '');
                        console.log("去掉文章开头的‘标题：’");
                    }

                    if (isWeb) {
                        if(type.indexOf("摘要") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                Artery.get('atyContainerAbsThink').show();
                                var array = splitThinkTag(text);
                                for(var i = 0; i < array.length; i++){
                                    if(array[i] === ''){
                                        continue
                                    }
                                    if(array[i].indexOf("<think>") > -1){
                                        Artery.get('atytextThinkAbsContent').setText(Artery.get('atytextThinkAbsContent').getText() + array[i].replace(THINKREG, ''));
                                    }else{
                                        Artery.get("atyTextareaByqgw").setValue(Artery.get("atyTextareaByqgw").getValue() + array[i]);
                                    }
                                }
                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextAbsDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                    Artery.get('atyTextLoading1').hide();
                                }
                                Artery.get("atyTextareaByqgw").setValue(Artery.get("atyTextareaByqgw").getValue() + text);
                            }
                            var e = document.querySelector("#atyRegionCenter_14e7b");
                            scrollToBottom(e);
                        }else if(type.indexOf("大纲") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                Artery.get('atyContainerOutlineThink').show();
                                var array = splitThinkTag(text);
                                for(var i = 0; i < array.length; i++){
                                    if(array[i] === ''){
                                        continue
                                    }
                                    if(array[i].indexOf("<think>") > -1){
                                        Artery.get('atytextThinkOutContent').setText(Artery.get('atytextThinkOutContent').getText() + array[i].replace(THINKREG, ''));
                                    }else{
                                        Artery.get("atyTextareaJhkli").setValue(Artery.get("atyTextareaJhkli").getValue() + array[i]);
                                    }
                                }
                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextLoading2').hide();
                                    Artery.get('atyTextOutDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                Artery.get("atyTextareaJhkli").setValue(Artery.get("atyTextareaJhkli").getValue() + text);
                            }
                            var e = document.querySelector("#atyRegionCenter_15e7b");
                            scrollToBottom(e);
                        }else if(type.indexOf("全文") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                // 写到思考过程中
                                if(type === "全文-1"){
                                    Artery.get('atyContainerDker').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkContent').setText(Artery.get('atytextThinkContent').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenterImgov");
                                    scrollToBottom(e);
                                }else if(type === "全文-3"){
                                    Artery.get('atyContainerOutlineThinkout').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkOutContentout').setText(Artery.get('atytextThinkOutContentout').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenter_15e7b");
                                    scrollToBottom(e);
                                }else if(type === "全文-2"){
                                    Artery.get('atyContainerAbsThinkabs').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkAbsContentabs').setText(Artery.get('atytextThinkAbsContentabs').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenter_14e7b");
                                    scrollToBottom(e);
                                }

                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextLoading').hide();
                                    Artery.get('atyTextLoading1').hide();
                                    Artery.get('atyTextLoading2').hide();
                                    Artery.get('atyTextLoading2out').hide();
                                    Artery.get('atyTextLoading1abs').hide();
                                }
                                if(costTime === -1 && type === "全文-1"){
                                    Artery.get('atyTextDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                if(costTime === -1 && type === "全文-2"){
                                    Artery.get('atyTextAbsDescabs').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                if(costTime === -1 && type === "全文-3"){
                                    Artery.get('atyTextOutDescout').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }

                                // console.log("xman 模型输出内容：" + text);

                                if ((tableText.length == 0 && text.indexOf("<<TABLE>>") > -1) || tableText.length > 0) {
                                    console.log("xman 模型输出内容包含表格 web ...");
                                    if(text.indexOf("<<TABLE>>") > 0){
                                        // 插入表格前的内容
                                        var beforeText = text.substring(0, text.indexOf("<<TABLE>>"));
                                        if (window.parent.insertToEditorAtEnd && beforeText.length > 0) {
                                            window.parent.insertToEditorAtEnd(beforeText + "\n", false, false);
                                        }
                                        tableText = tableText + text.substring(text.indexOf("<<TABLE>>"));
                                    } else {
                                        tableText = tableText + text;
                                    }
                                    if (text.indexOf("<<END_TABLE>>") > -1) {
                                        var tableHtml = tableBlockToHtml(tableText);
                                        if (window.parent.insertToEditorAtEnd) {
                                            window.parent.insertToEditorAtEnd(tableHtml, false, false);
                                        }
                                        // 插入表格后的内容
                                        cache = cache + text.substring(text.indexOf("<<END_TABLE>>") + 13).trim();
                                        tableText = "";
                                    }
                                } else {
                                    var text_zw = '';
                                    if(responseText.length > 0) {
                                        if(cache !== ''){
                                            text_zw = cache + text;
                                            cache = ''
                                        }else{
                                            text_zw = text;
                                        }
                                    } else {
                                        if(cache !== ""){
                                            text_zw = "<p class='select-start'>" + cache + text;
                                            cache = '';
                                        }else{
                                            text_zw = "<p class='select-start'>" + text;
                                        }
                                    }

                                    text_zw = text_zw.replace(/  +/g, '')
                                    if (window.parent.insertToEditorAtEnd) {
                                        window.parent.insertToEditorAtEnd(text_zw, false, false);
                                    }
                                }

                                responseText = responseText + text;
                            }
                        }
                    } else if(!wpsinner) {
                        if(type.indexOf("摘要") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                Artery.get('atyContainerAbsThink').show();
                                var array = splitThinkTag(text);
                                for(var i = 0; i < array.length; i++){
                                    if(array[i] === ''){
                                        continue
                                    }
                                    if(array[i].indexOf("<think>") > -1){
                                        Artery.get('atytextThinkAbsContent').setText(Artery.get('atytextThinkAbsContent').getText() + array[i].replace(THINKREG, ''));
                                    }else{
                                        Artery.get("atyTextareaByqgw").setValue(Artery.get("atyTextareaByqgw").getValue() + array[i]);
                                    }
                                }
                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextAbsDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                    Artery.get('atyTextLoading1').hide();
                                }
                                Artery.get("atyTextareaByqgw").setValue(Artery.get("atyTextareaByqgw").getValue() + text);
                            }
                            var e = document.querySelector("#atyRegionCenter_14e7b");
                            scrollToBottom(e);
                        }else if(type.indexOf("大纲") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                Artery.get('atyContainerOutlineThink').show();
                                var array = splitThinkTag(text);
                                for(var i = 0; i < array.length; i++){
                                    if(array[i] === ''){
                                        continue
                                    }
                                    if(array[i].indexOf("<think>") > -1){
                                        Artery.get('atytextThinkOutContent').setText(Artery.get('atytextThinkOutContent').getText() + array[i].replace(THINKREG, ''));
                                    }else{
                                        Artery.get("atyTextareaJhkli").setValue(Artery.get("atyTextareaJhkli").getValue() + array[i]);
                                    }
                                }
                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextLoading2').hide();
                                    Artery.get('atyTextOutDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                Artery.get("atyTextareaJhkli").setValue(Artery.get("atyTextareaJhkli").getValue() + text);
                            }
                            var e = document.querySelector("#atyRegionCenter_15e7b");
                            scrollToBottom(e);
                        }else if(type.indexOf("全文") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                // 写到思考过程中
                                if(type === "全文-1"){
                                    Artery.get('atyContainerDker').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkContent').setText(Artery.get('atytextThinkContent').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenterImgov");
                                    scrollToBottom(e);
                                }else if(type === "全文-3"){
                                    Artery.get('atyContainerOutlineThinkout').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkOutContentout').setText(Artery.get('atytextThinkOutContentout').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenter_15e7b");
                                    scrollToBottom(e);
                                }else if(type === "全文-2"){
                                    Artery.get('atyContainerAbsThinkabs').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkAbsContentabs').setText(Artery.get('atytextThinkAbsContentabs').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenter_14e7b");
                                    scrollToBottom(e);
                                }
                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextLoading').hide();
                                    Artery.get('atyTextLoading1').hide();
                                    Artery.get('atyTextLoading2').hide();
                                    Artery.get('atyTextLoading2out').hide();
                                    Artery.get('atyTextLoading1abs').hide();
                                }
                                if(costTime === -1 && type === "全文-1"){
                                    Artery.get('atyTextDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                if(costTime === -1 && type === "全文-2"){
                                    Artery.get('atyTextAbsDescabs').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                if(costTime === -1 && type === "全文-3"){
                                    Artery.get('atyTextOutDescout').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                
                                if ((tableText.length == 0 && text.indexOf("<<TABLE>>") > -1) || tableText.length > 0) {
                                    console.log("xman 模型输出内容包含表格 word ...");
                                    if(text.indexOf("<<TABLE>>") > 0){
                                        var beforeText = text.substring(0, text.indexOf("<<TABLE>>"));
                                        window.parent.aiWriter.appendTextInSelection(beforeText + "\n");
                                        tableText = tableText + text.substring(text.indexOf("<<TABLE>>"));
                                    } else {
                                        tableText = tableText + text;
                                    }
                                    if (text.indexOf("<<END_TABLE>>") > -1) {
                                        // console.log("xman 输出表格：" + tableText);
                                        console.log("word 忽略表格输出 ...");
                                        window.parent.aiWriter.appendTextInSelection(tableText);
                                        // 插入表格后的内容
                                        cache = cache + text.substring(text.indexOf("<<END_TABLE>>") + 13).trim();
                                        tableText = "";
                                    }
                                } else {
                                    if(cache !== ''){
                                        window.parent.aiWriter.appendTextInSelection(cache + text);
                                        cache = '';
                                    }else{
                                        window.parent.aiWriter.appendTextInSelection(text);
                                    }
                                }
                            }
                        }
                    }else {
                        if(type.indexOf("摘要") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                Artery.get('atyContainerAbsThink').show();
                                var array = splitThinkTag(text);
                                for(var i = 0; i < array.length; i++){
                                    if(array[i] === ''){
                                        continue
                                    }
                                    if(array[i].indexOf("<think>") > -1){
                                        Artery.get('atytextThinkAbsContent').setText(Artery.get('atytextThinkAbsContent').getText() + array[i].replace(THINKREG, ''));
                                    }else{
                                        Artery.get("atyTextareaByqgw").setValue(Artery.get("atyTextareaByqgw").getValue() + array[i]);
                                    }
                                }
                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextLoading1').hide();
                                    Artery.get('atyTextAbsDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                Artery.get("atyTextareaByqgw").setValue(Artery.get("atyTextareaByqgw").getValue() + text);
                            }
                            var e = document.querySelector("#atyRegionCenter_14e7b");
                            scrollToBottom(e);
                        }else if(type.indexOf("大纲") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                Artery.get('atyContainerOutlineThink').show();
                                var array = splitThinkTag(text);
                                for(var i = 0; i < array.length; i++){
                                    if(array[i] === ''){
                                        continue
                                    }
                                    if(array[i].indexOf("<think>") > -1){
                                        Artery.get('atytextThinkOutContent').setText(Artery.get('atytextThinkOutContent').getText() + array[i].replace(THINKREG, ''));
                                    }else{
                                        Artery.get("atyTextareaJhkli").setValue(Artery.get("atyTextareaJhkli").getValue() + array[i]);
                                    }
                                }
                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextLoading2').hide();
                                    Artery.get('atyTextOutDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                Artery.get("atyTextareaJhkli").setValue(Artery.get("atyTextareaJhkli").getValue() + text);
                            }
                            var e = document.querySelector("#atyRegionCenter_15e7b");
                            scrollToBottom(e);
                        }else if(type.indexOf("全文") > -1){
                            var text = request.responseText.slice(lastPosition).replace(/\s+\n/g, "\n");
                            lastPosition = request.responseText.length;
                            if(text.indexOf("<think>") > -1 && text.indexOf("</think>") > -1){
                                // 写到思考过程中
                                if(type === "全文-1"){
                                    Artery.get('atyContainerDker').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkContent').setText(Artery.get('atytextThinkContent').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenterImgov");
                                    scrollToBottom(e);
                                }else if(type === "全文-3"){
                                    Artery.get('atyContainerOutlineThinkout').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkOutContentout').setText(Artery.get('atytextThinkOutContentout').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenter_15e7b");
                                    scrollToBottom(e);
                                }else if(type === "全文-2"){
                                    Artery.get('atyContainerAbsThinkabs').show();
                                    var array = splitThinkTag(text);
                                    for(var i = 0; i < array.length; i++){
                                        if(array[i] === ''){
                                            continue
                                        }
                                        if(array[i].indexOf("<think>") > -1){
                                            Artery.get('atytextThinkAbsContentabs').setText(Artery.get('atytextThinkAbsContentabs').getText() + array[i].replace(THINKREG, ''));
                                        }else{
                                            cache += array[i]
                                        }
                                    }
                                    var e = document.querySelector("#atyRegionCenter_14e7b");
                                    scrollToBottom(e);
                                }
                            }else{
                                if(costTime === -1){
                                    Artery.get('atyTextLoading').hide();
                                    Artery.get('atyTextLoading1').hide();
                                    Artery.get('atyTextLoading2').hide();
                                    Artery.get('atyTextLoading2out').hide();
                                    Artery.get('atyTextLoading1abs').hide();
                                }
                                if(costTime === -1 && type === "全文-1"){
                                    Artery.get('atyTextDesc').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                if(costTime === -1 && type === "全文-2"){
                                    Artery.get('atyTextAbsDescabs').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                if(costTime === -1 && type === "全文-3"){
                                    Artery.get('atyTextOutDescout').setText("已深度思考（用时 " + (costTime = Math.floor((new Date().getTime() - statrTime) / 1000)) + " 秒）");
                                }
                                if (app === undefined) {
                                    app = wps_.WpsApplication();
                                }
                                app.ScreenUpdating = false;
                                var doc = app.ActiveDocument;
                                if (doc){
                                    text = _continueWriteTime > 0 ? "\n" + text : text;
                                    var range = doc.Range(doc.Content.End, doc.Content.End);
                                    // text = "<<TABLE>>\n{测试1 | 测试2 | 测试3}\n测试4 | 测试5 | 测试6\n测试7 | 测试8 | 测试9\n<<END_TABLE>>\n";
                                    // tableText = text;
                                    if ((tableText.length == 0 && text.indexOf("<<TABLE>>") > -1) || tableText.length > 0) {
                                        Artery.alert.warning("xman 模型输出内容包含表格 wps ...");
                                        if(text.indexOf("<<TABLE>>") > 0){
                                            var beforeText = text.substring(0, text.indexOf("<<TABLE>>"));
                                            if (beforeText.length > 0) {
                                                range.InsertBefore(beforeText + "\n");
                                                range = doc.Range(doc.Content.End, doc.Content.End);
                                            }
                                            tableText = tableText + text.substring(text.indexOf("<<TABLE>>"));
                                        } else {
                                            tableText = tableText + text;
                                        }
                                        if (text.indexOf("<<END_TABLE>>") > -1) {
                                            // Artery.alert.warning("xman 输出表格：" + tableText);
                                            const content = doc.Content;
                                            content.Collapse(0); // 0 表示 collapse 到末尾（WPS 中常为 wdCollapseEnd）
                                            content.InsertAfter("\n"); // 插入一个换行，确保有段落
                                            const range = doc.Range(content.End - 1, content.End - 1); 
                                            insertWpsTable(doc, tableText, range);
                                            // 插入表格后的内容
                                            cache = cache + text.substring(text.indexOf("<<END_TABLE>>") + 13).trim();
                                            tableText = "";
                                        }
                                    } else {
                                        if(cache !== ''){
                                            text = cache + text;
                                            cache = ''
                                        }
                                        // Artery.alert.warning("xman 模型输出内容：" + text);
                                        range.InsertBefore(text);
                                    }
                                    range = app.Selection.SetRange(doc.Content.End, doc.Content.End);
                                    app.ScreenRefresh();                                   
                                }
                                app.ScreenUpdating = true;
                            }
                        }
                    }
                }
                if(request.readyState === 4) {
                    let isChatOpenLabel = true;
                    if(Artery.customParams && Artery.customParams.isChatOpenLabel !== null){
                        isChatOpenLabel = Artery.customParams.isChatOpenLabel;
                    }
                    console.log("删除属性")
                    $('#atyTextareaJhkli .aty-input-common').removeAttr('title');
                    $('#atyTextareaByqgw .aty-input-common').removeAttr('title');
                    if(request.responseText.startsWith('权益错误：')) {
                        Artery.getTopWindow().showBenefitDialog();
                        console.log(request.responseText);
                        requesting = false;
                        showDone();
                        showDoneButtonGroup(type);
                        return;
                    }
                    if(_continueWriteTime > 0){
                        if(_continueWriteTempText === ""){
                            _continueWriteTempText = request.responseText;
                        }
                    }else{
                        if(!_stopflag){
                            _continueWriteTempText = "";
                        }
                    }
                    if (isWeb) {
                        requesting = false;
                        if(type.indexOf("全文") > -1){
                            if (window.parent.insertToEditor) {
                                window.parent.insertToEditor("<p class='select-end'>", false, false, true, Artery.get('atyInputYkjao').getValue());
                                let text = window.parent.getEditorContentText();
                                if(text !== undefined && text.trim() !== "" && isChatOpenLabel){
                                    window.parent.insertToEditor("<br/>以上内容为AI生成，仅供参考使用", false, false, false,'');
                                }
                            }
                            if (window.parent.selectInEditor) {
                                window.parent.selectInEditor();
                            }
                            if (window.parent.format) {
                                window.parent.format();
                            }
                        }
                        var dg = Artery.get('atyTextareaJhkli').getValue();
                        if(dg !== '' && isChatOpenLabel){
                            Artery.get('atyTextareaJhkli').setValue(dg + '\n以上内容为AI生成，仅供参考使用');
                        }
                        showDone();
                        showDoneButtonGroup(type);
                    } else if(!wpsinner) {
                        if(type.indexOf("全文") > -1){
                            window.parent.aiWriter.appendEnd();
                        }
                        requesting = false;
                        showDone();
                        showDoneButtonGroup(type);
                    }else{
                        if(type.indexOf("全文") > -1){
                            var app = wps_.WpsApplication();
                            var doc = app.ActiveDocument;
                            if (doc && isChatOpenLabel) {
                                let text = doc.Range(0, doc.Content.End).Text;
                                if (text !== undefined && text !== "" && text.trim() !== "") {
                                    var insertPos = doc.Content.End;
                                    var range = doc.Range(insertPos, insertPos);
                                    var disclaimerText = "\n以上内容为AI生成，仅供参考使用";
                                    range.InsertBefore(disclaimerText);
                                    var newEnd = doc.Content.End;
                                    var textLength = "以上内容为AI生成，仅供参考使用".length;
                                    var newRange = doc.Range(newEnd - textLength - 2, newEnd);
                                    newRange.Font.Name = "宋体";
                                    newRange.Font.Size = 18;
                                    newRange.Font.Bold = true;
                                    newRange.Font.Color = 0x7D7D7D;
                                }
                            }
                        }
                        var dg = Artery.get('atyTextareaJhkli').getValue();
                        if(dg !== '' && isChatOpenLabel){
                            Artery.get('atyTextareaJhkli').setValue(dg + '以上内容为AI生成，仅供参考使用');
                        }
                        requesting = false;
                        showDone();
                        showDoneButtonGroup(type);
                    }
                }
            })
        });
    })

}

function searchDifyZsk(type, title, cause, callback) {
    if(onlinesearch) {
        var onlinesearchDataComponentId = '';
        var componentId = '';
        var pId = '';
        var cId = '';
        if(type.indexOf("全文-1") > -1) {
            componentId = 'atyContainerZkxrq';
            pId = 'atyContainerSiomi';
            cId = 'atyContainerTnuka';
            onlinesearchDataComponentId = 'atyHiddenEpiny_1';
        }
        if(type.indexOf("全文-2") > -1) {
            componentId = 'atyContainerAkvgx';
            pId= 'atyContainerFgcli';
            cId = 'atyContainerSvema';
            onlinesearchDataComponentId = 'atyHiddenEpiny_2';
        }
        if(type.indexOf("全文-3") > -1) {
            componentId = 'atyContainerTxpww';
            pId= 'atyContainerRcwsq';
            cId = 'atyContainerXagnv';
            onlinesearchDataComponentId = 'atyHiddenEpiny_3';
        }
        if(componentId !== '' && pId !== '' && cId !== '') {
            Artery.parseComponent({
                data: {
                    componentId: componentId,
                    params: JSON.stringify(
                        {
                            "title": title,
                            "cause": cause,
                            "tenantCode": tenantCode ? tenantCode : "",
                            "loginId": loginId ? loginId : "",
                            "ragZskIndex": hmdRagZsk.join(','),
                            // 是否最高简版页面嵌入的
                            "simple": 'simple' in Artery.getParentArtery().params && Artery.getParentArtery().params.simple === 'true'
                        })
                },
                scope: this,
                async: true,
                callback: function (result, jqXHR, textStatus) {
                    if (result && result.html && result.html.indexOf(cId + '_AcloneA_') !== -1) {
                        Artery.get(pId).removeAll();
                        Artery.get(pId).addParsedComponent(result); //将生成的控件添加到容器中
                        Artery.get(pId).show();
                        var onlinesearchDataId = '';
                        if(onlinesearchDataComponentId.length > 0 && Artery.get(onlinesearchDataComponentId+'_AcloneA_0')) {
                            onlinesearchDataId = Artery.get(onlinesearchDataComponentId+'_AcloneA_0').getValue();
                        }
                        callback(onlinesearchDataId);
                    } else {
                        Artery.get(pId).hide();
                        callback('');
                    }
                }
            });
        }else {
            Artery.get('atyContainerSiomi').hide();
            Artery.get('atyContainerFgcli').hide();
            Artery.get('atyContainerRcwsq').hide();
            callback('');
        }
    }else {
        callback('');
    }
}

function uploadFiles(callback) {
    if(!wpsinner) {
        Artery.get("atyUploadAejkv1").upload({
            callback: function(result) {
                if(Artery.get("atyUploadAejkv2")){
                    Artery.get("atyUploadAejkv2").upload({
                        callback: function(result2) {
                            if(result.length > 0){
                                result2.push(result[0]);
                            }
                            callback(result2);
                        }
                    });
                }
                if(Artery.get("atyUploadAejkv2hmd")){
                    Artery.get("atyUploadAejkv2hmd").upload({
                        callback: function(result2) {
                            if(result.length > 0){
                                result2.push(result[0]);
                            }
                            callback(result2);
                        }
                    });
                }
            }
        });
    }else {
        var keys = Object.keys(_xcfiles);
        if(keys.length === 0) {
            callback([]);
            return;
        }
        var data = new FakeFormData();
        for(var i = 0; i < keys.length; i++) {
            const fileindex = i;
            const filedata = wps_.FileSystem.readAsBinaryString(_xcfiles[keys[fileindex]]);
            data.append("files", {
                name: utf16ToUtf8(keys[fileindex]), //主要是考虑中文名的情况，服务端约定用utf-8来解码。
                type: "application/octet-stream",
                getAsBinary: function () {
                    return filedata;
                }
            });
        }
        _xcfiles = {};
        var request = new XMLHttpRequest();
        var _method = 'POST';
        var _url = 'client/fdqc/xcupload';
        request.open(_method, _url, true);
        request.timeout = 3600000;// 60分钟
        request.onreadystatechange = function () {
            /**
             * request.readyState
             * 0: 请求未初始化
             * 1: 服务器连接已建立
             * 2: 请求已接收
             * 3: 请求处理中
             * 4: 请求已完成，且响应已就绪
             */
            if(request.readyState === 4) {
                callback(JSON.parse(request.responseText));
            }
        }
        request.onerror = function handleError() {
            console.log("request error");
        }
        request.ontimeout = function handleTimeout() {
            console.error('timeout of '+request.timeout+' ms exceeded');
        }
        request.setRequestHeader("Cache-Control", "no-cache");
        request.setRequestHeader("X-Requested-With", "XMLHttpRequest");
        if (data.fake) {
            request.setRequestHeader("Content-Type", "multipart/form-data; boundary=" + data.boundary);
            var arr = StringToUint8Array(data.toString());
            request.send(arr);
        } else {
            request.send(data);
        }
    }
}


function getFilePaths() {
    var filepaths = [];
    Object.keys(_files).map(function (filename) {
        filepaths.push(filename.split('_')[0] + '_' + _files[filename]);
    })
    return filepaths;
}

function isCkgs(){
    var flag = false;
    Object.keys(_files).map(function (filename) {
        if(filename.split('_')[0] === 'ckgs'){
            flag = true;
        }
    })
    return flag;
}

function getCkgs(){
    fp = ''
    Object.keys(_files).map(function (filename) {
        if(filename.split('_')[0] === 'ckgs'){
            fp = _files[filename]
        }
    })
    return fp;
}



function request(type,  gwwz, title, cause, zhailuids, shoucangids, luyinids, filepaths, abstract, outline, exchange, isUseDwxx, zsnum, fwck, llmMode, onlinesearchDataId, callback) {
    if(_continueWriteTime === 0 || type.indexOf("全文") === -1) {   // 没写完的情况不更新id
        _id = getUuid();
    }
    var request = new XMLHttpRequest();
    var _method = 'POST';
    var _url = 'client/fdqc/chat';
    request.open(_method, _url, true);
    request.timeout = 3600000;// 60分钟
    request.onreadystatechange = function () {
        /**
         * request.readyState
         * 0: 请求未初始化
         * 1: 服务器连接已建立
         * 2: 请求已接收
         * 3: 请求处理中
         * 4: 请求已完成，且响应已就绪
         */
        callback(request);
    }
    request.onerror = function handleError() {
        console.log("request error");
        requesting = false;
    }
    request.ontimeout = function handleTimeout() {
        console.error('timeout of '+request.timeout+' ms exceeded');
        requesting = false;
    }
    let isWps = isWeb? false : true;
    // 新版起草页，支持大纲填写，这里判断下
    if(type === '全文-1' && Artery.get('atyTextareaEjhta') && Artery.get('atyTextareaEjhta').getValue().length > 0) {
        type = '全文-3';
        outline = Artery.get('atyTextareaEjhta').getValue();
    }
    request.send(JSON.stringify(
        {
                "type":type,
                "gwwz":gwwz,
                "title":title,
                "cause":cause,
                "zhailuids": zhailuids,
                "shoucangids": shoucangids,
                "luyinids": luyinids,
                "filepaths":filepaths,
                "abstract":abstract,
                "outline":outline,
                "isUseDwxx": isUseDwxx,
                "id":_id,
                "continueWriteTime":_continueWriteTime,
                "continueWriteTempText":_continueWriteTempText,
                "exchange":exchange,
                "tenantCode": tenantCode?tenantCode:"",
                "loginId":loginId?loginId:"",
                "isWps":isWps,
                "wordCount":zsnum,
                "fwckContent":fwck.fxnr_txt,
                "tplContent": TJJGCK.tplContent,
                "llmMode": llmMode,
                "needSearch": onlinesearch,
                "needFineSearch": onlinefinesearch,
                // 是否最高简版页面嵌入的
                "simple": 'simple' in Artery.getParentArtery().params && Artery.getParentArtery().params.simple === 'true',
                "onlinesearchDataId": onlinesearchDataId,
                "evaluationInfo": evaData,
            }));
    _request = request;
    _scrollToBottom = true;
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


function showLoading(t) {
    if(t === 2){
        Artery.get('atyRahgemNorthloading').show();
        Artery.get('atyRahgemNorthloading1').hide();
        Artery.get("atyContainerOdrgi").show();
        Artery.get("atyContainerOdrgi1").hide();
        Artery.get("atyContainerOdrgi2").hide();
    }else if(t === 1){
        Artery.get('atyRahgemNorthloading').hide();
        Artery.get('atyRahgemNorthloading1').hide();
        Artery.get("atyContainerOdrgi2").show();
        Artery.get("atyContainerOdrgi1").hide();
        Artery.get("atyContainerOdrgi").hide();
    }else{
        Artery.get('atyRahgemNorthloading').hide();
        Artery.get('atyRahgemNorthloading1').show();
        Artery.get("atyContainerOdrgi2").hide();
        Artery.get("atyContainerOdrgi1").show();
        Artery.get("atyContainerOdrgi").hide();
    }
    Artery.get("atyContainerUjeny2").hide();
    Artery.get("atyContainerUjeny1").hide();
    Artery.get("atyContainerUjeny").hide();
}

function showDone() {
    Artery.get("atyContainerOdrgi").hide();
    Artery.get("atyContainerOdrgi1").hide();
    Artery.get("atyContainerOdrgi2").hide();
    Artery.get('atyRahgemNorthloading').hide()
    Artery.get('atyRahgemNorthloading1').hide();
    showDoneButtonGroup()
}


function showDoneButtonGroup(){
    if(ctype === "全文-1"){
        Artery.get('atyRahgemNorthloading').hide();
        Artery.get('atyRahgemNorthloading1').hide();
        Artery.get('atyContainerUjeny1').hide();
        Artery.get('atyContainerUjeny').hide();
        Artery.get('atyContainerUjeny2').show();
        if (isCkgs() || FXCK.fxnr_id !== ""){
            Artery.get('atyButtonGkdoi2').show();
        }
    }
    if(ctype === "全文-2"){
        Artery.get('atyRahgemNorthloading').hide();
        Artery.get('atyRahgemNorthloading1').hide();
        Artery.get('atyContainerUjeny1').hide();
        Artery.get('atyContainerUjeny').show();
        Artery.get('atyContainerUjeny2').hide();
        if (isCkgs() || FXCK.fxnr_id !== ""){
            Artery.get('atyButtonGkdoi').show();
        }
    }
    if(ctype === "全文-3"){
        Artery.get('atyRahgemNorthloading').hide();
        Artery.get('atyRahgemNorthloading1').hide();
        Artery.get('atyContainerUjeny1').show();
        Artery.get('atyContainerUjeny').hide();
        Artery.get('atyContainerUjeny2').hide();
        if (isCkgs() || FXCK.fxnr_id !== ""){
            Artery.get('atyButtonGkdoi1').show();
        }
    }
}

function closeDoneButtonGroup(){
    //影藏按钮
    if($('#atyContainerOfkbc1').is(':visible')){
        var btn = document.getElementById("atyIconArfwc1")
        btn.classList.remove("ckxx-btn-icon-down");
        btn.classList.add("ckxx-btn-icon-up");
        Artery.get("atyContainerOfkbc1").hide()
        Artery.get('atyRahgemNorthloading').hide();
        Artery.get('atyRahgemNorthloading1').hide();
    }

    if($('#atyContainerOfkbc').is(':visible')){
        var btn = document.getElementById("atyIconArfwc")
        btn.classList.remove("ckxx-btn-icon-down");
        btn.classList.add("ckxx-btn-icon-up");
        Artery.get("atyContainerOfkbc").hide()
    }

    Artery.get('atyContainerUjeny1').hide();
    Artery.get('atyContainerUjeny').hide();
    Artery.get('atyContainerUjeny2').hide();
}

function closeLoadingButtonGroup(){
    Artery.get('atyContainerOdrgi').hide();
    Artery.get('atyContainerOdrgi1').hide();
    Artery.get('atyContainerOdrgi2').hide();
    Artery.get('atyRahgemNorthloading').hide();
    Artery.get('atyRahgemNorthloading1').hide();
}

/**
 * 是否正在生成
 */
window.isRequesting = function () {
    return requesting;
}
/**
 * 客户端插入完成回调
 */
window.c_appendEnd = function () {
    showDone();
    requesting = false;
}
/**
 * 点击时触发客户端脚本(atyIconXgeki)
 * 点击生成时候的停止
 * @param rc 系统提供的AJAX调用对象
 */
function atyIconXgekiOnClickClient(rc) {
    _stopflag = true;
    if(!wpsinner && !isWeb) {
        window.parent.aiWriter.stopAppend();
    }
    if(_request !== null){
        _request.abort();
        _request = null;
    }
    requesting = false;
    showDone();
}

/**
 * 点击时脚本(atyButtonNppxn)
 * 大纲内容插入到正文中
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonNppxnOnClickClient(rc) {
    insertContent2Doc("atyTextareaJhkli")
}

/**
 * 点击时脚本(atyButtonTinrl)
 * 摘要内容插入到文本中
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonTinrlOnClickClient(rc) {
    insertContent2Doc("atyTextareaByqgw")
}

function insertContent2Doc(id){
    var divText = Artery.get(id).getValue();
    if(wpsinner) {
        var app = wps_.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc){
            if(app.Selection.Start !== app.Selection.End) {
                doc.Range(app.Selection.Start, app.Selection.End).Delete();
            }
            var range = doc.Range(app.Selection.End, app.Selection.End);
            range.InsertBefore(divText);
            app.Selection.SetRange(app.Selection.End+divText.length,app.Selection.End+divText.length)
        }
    }else if(isWeb) {
        if (window.parent.insertToEditor) {
            // divText = divText.replace(/\n/g, "</p><p>");
            // divText = "<p>" + divText + "</p>"
            window.parent.insertToEditor(divText, false, true, true, Artery.get('atyInputYkjao').getValue());
        }
        if(window.parent.format) {
            window.parent.format(true);
        }
    }else {
        window.parent.aiWriter.insertText(divText, function(resultObj) { // resultObj 是 json 字符串
            var res = JSON.parse(resultObj);
            if(res.ok) {
                console.log("插入成功");
            }else {
                console.log(r.message);
            }
        })
    }
}
/**
 * 点击时脚本(atyButtonFqoxr2)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonFqoxr2OnClickClient(rc) {
    Artery.get('atyContainerUjeny1').hide();
    Artery.get('atyContainerUjeny').hide();
    Artery.get('atyContainerUjeny2').hide();
}

/**
 * 点击时脚本(atyButtonIqydcd)
 * 摘要换一换
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonIqydcdOnClickClient(rc) {
    //清空摘要内容
    Artery.get("atyTextareaByqgw").setValue('');
    resetContaine()
    generateDocument("摘要-2", true)
}

/**
 * 点击时脚本(atyButtonIqyqc)
 * 点击大纲换一换
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonIqyqcOnClickClient(rc) {
    //清空摘要内容
    Artery.get("atyTextareaJhkli").setValue('');
    resetContaine()
    generateDocument("大纲-2", true, true)
}

/**
 * 点击时脚本(atyButtonAmyfu1)
 * 点击大纲中的换一换按钮
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonAmyfu1OnClickClient(rc) {
    exchangeContent(rc, "3")
}

/**
 * 点击时脚本(atyButtonAmyfu2)
 * 首页的换一换
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonAmyfu2OnClickClient(rc) {
    changeContent(rc, "全文-1")
}

function changeContent(rc, type){
    if(isWeb){
        let text = window.parent.getEditorContentText()
        if(text !== undefined && text !== ""){
            window.parent.showTextModel(type);
        }else{
            exchangeContent(rc, "1")
        }
    }else if(wpsinner){
        var app = wps_.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc){
            let text = doc.Range(0, doc.Content.End).Text;
            if(text !== undefined && text !== "" && text.trim() !== "") {
                var result = confirm("当前内容区域有内容，默认会追加内容到光标处。是否清空内容后开始写作");
                if (result) {
                    var app = wps_.WpsApplication();
                    var doc = app.ActiveDocument;
                    if (doc) {
                        doc.Range(0, doc.Content.End).Delete()
                        exchangeContent(rc, "1")
                    }
                } else {
                    exchangeContent(rc, "1")
                }
            }else{
                exchangeContent(rc, "1")
            }
        }
    }else{
        exchangeContent(rc, "1")
    }
}

/**
 * 点击时脚本(atyButtonAmyfu)
 * 摘要中的换一换
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonAmyfuOnClickClient(rc) {
    exchangeContent(rc, "2")
}

function exchangeContent(rc, type){
    _stopflag = false;
    closeDoneButtonGroup();
    if (isWeb) {
        if (window.parent.insertToEditor) {
            window.parent.insertToEditor('', false, true, true, Artery.get('atyInputYkjao').getValue());
        }
        if(window.parent.format) {
            window.parent.format(true);
        }
    } else if(!wpsinner) {
        window.parent.aiWriter.deleteSelectedText();
    }else {
        var app = wps_.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc){
            var start = app.Selection.Start;
            var end = app.Selection.End;
            doc.Range(start, end).Delete();
        }
    }
    _continueWriteTime = 0;// 点换一换的时候重置下
    generateDocument("全文-" + type, true);
}

/**
 * 点击时脚本(atyButtonCmmpd2)
 * 继续-1
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonCmmpd2OnClickClient(rc) {
    _continueWriteTime = _continueWriteTime + 1;
    if(isWeb) {
        if (window.parent.getEditorContentTextForBj) {
            _continueWriteTempText = window.parent.getEditorContentTextForBj();
        }
        generateDocument("全文-1", false);
    } else if(!wpsinner) {
        //追加到临时写作的目录
        try {
            window.parent.aiWriter.getDocumentContent("", function (resultObj){
                var res = JSON.parse(resultObj);
                if(res.ok){
                    _continueWriteTempText = res.content;
                }
                generateDocument("全文-1", false);
            })
        }catch (error){
        }
    }else{
        var app = wps_.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc){
            _continueWriteTempText = doc.Range(0, doc.Words.Count).Text;
        }
        generateDocument("全文-1", false);
    }
}

/**
 * 点击时脚本(atyButtonCmmpd)
 * 继续-2
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonCmmpdOnClickClient(rc) {
    _continueWriteTime = _continueWriteTime + 1;
    if(isWeb) {
        if (window.parent.getEditorContentTextForBj) {
            _continueWriteTempText = window.parent.getEditorContentTextForBj();
        }
    } else if(!wpsinner) {
        //追加到临时写作的目录
        try {
            window.parent.aiWriter.getDocumentContent("", function (resultObj){
                var res = JSON.parse(resultObj);
                if(res.ok){
                    _continueWriteTempText = res.content;
                }
            })
        }catch (error){
        }
    }else{
        var app = wps_.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc){
            _continueWriteTempText = doc.Range(0, doc.Words.Count).Text;
        }
    }
    generateDocument("全文-2", false);
}

/**
 * 点击时脚本(atyButtonCmmpd1)
 * 继续-3
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonCmmpd1OnClickClient(rc) {
    _continueWriteTime = _continueWriteTime + 1;
    if(isWeb) {
        if (window.parent.getEditorContentTextForBj) {
            _continueWriteTempText = window.parent.getEditorContentTextForBj();
        }
    } else if(!wpsinner) {
        //追加到临时写作的目录
        try {
            window.parent.aiWriter.getDocumentContent("", function (resultObj){
                var res = JSON.parse(resultObj);
                if(res.ok){
                    _continueWriteTempText = res.content;
                }
            })
        }catch (error){
        }
    }else{
        var app = wps_.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc){
            _continueWriteTempText = doc.Range(0, doc.Words.Count).Text;
        }
    }
    generateDocument("全文-3", false);
}


/**
 * 点击时脚本(atyButtonTttcj2)
 * 弃用（空着就行）
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonTttcjOnClickClient(rc) {

}


/**
 * 点击时脚本(atyButtonAogeq2)
 * 弃用（空着就行）
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonAogeqOnClickClient(rc) {

}
/**
 * 点击时触发客户端脚本(atyContainerKgkrp)
 * 点击参考信息列表按钮
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerKgkrpOnClickClient(rc) {

    if($('#atyContainerOfkbc1').is(':visible')){
        var btn = document.getElementById("atyIconArfwc1")
        btn.classList.remove("ckxx-btn-icon-down");
        btn.classList.add("ckxx-btn-icon-up");
        Artery.get("atyContainerOfkbc1").hide()
    }

    var box = document.getElementById("atyContainerOfkbc")
    var btn = document.getElementById("atyIconArfwc")
    if(box.offsetParent === null){
        setzlxxHieght()
        Artery.get("atyContainerOfkbc").show();
        btn.classList.remove("ckxx-btn-icon-up");
        btn.classList.add("ckxx-btn-icon-down");
        endTime = formatDate(new Date(), 'yyyy-MM-dd');
        // 加载数据
        // 判断一下，如果已经有内容，就不重新请求了
        if($("#atyCheckboxDebgu").find("tr").length > 0){
            //记录信息
            let ids = Artery.get('atyCheckboxDebgu').getValue();
            let idsArray = ids.split(',');
            // 刷新列表
            getCheckboxOptions(rc, startTime, endTime, 'zlnr', idsArray);
            // setTimeout(function (){
            //     let id_new = ""
            //     $("#atyCheckboxDebgu .aty-checkbox-input").each(function (){
            //         let value = $(this).attr("value")
            //         for(var i = 0; i < idsArray.length; i++){
            //             if(value === idsArray[i]){
            //                 id_new += (value + ",")
            //             }
            //         }
            //     });
            //     Artery.get("atyCheckboxDebgu").setValue(id_new);
            // }, 100)

        }else{
            getCheckboxOptions(rc, startTime, endTime, 'zlnr');
        }
    }else{
        btn.classList.remove("ckxx-btn-icon-down");
        btn.classList.add("ckxx-btn-icon-up");
        Artery.get("atyContainerOfkbc").hide()
    }
}

/**
 * 点击时触发客户端脚本(atyContainerKgkrp)
 * 点击参考信息列表按钮
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerKgkrp1OnClickClient(rc) {

    if($('#atyContainerOfkbc').is(':visible')){
        var btn = document.getElementById("atyIconArfwc")
        btn.classList.remove("ckxx-btn-icon-down");
        btn.classList.add("ckxx-btn-icon-up");
        Artery.get("atyContainerOfkbc").hide()
    }

    var box = document.getElementById("atyContainerOfkbc1")
    var btn = document.getElementById("atyIconArfwc1")
    if(box.offsetParent === null){
        setzlxxHieght1()
        Artery.get("atyContainerOfkbc1").show();
        btn.classList.remove("ckxx-btn-icon-up");
        btn.classList.add("ckxx-btn-icon-down");
        endTime = formatDate(new Date(), 'yyyy-MM-dd');
        // 加载数据
        let ids = Artery.get('atyCheckboxDebgu1').getValue();
        let idsArray = ids.split(',');
        // 刷新列表
        getCheckboxOptions(rc, startTime, endTime, 'scnr', idsArray);
        //
        //
        // // 判断一下，如果已经有内容，就不重新请求了
        // if($("#atyCheckboxDebgu1").find("tr").length > 0){
        //     //记录信息
        //
        // }else{
        //     getCheckboxOptions(rc, startTime, endTime, 'scnr');
        // }
    }else{
        btn.classList.remove("ckxx-btn-icon-down");
        btn.classList.add("ckxx-btn-icon-up");
        Artery.get("atyContainerOfkbc1").hide()
    }
}


/**
 * 点击时触发客户端脚本(atyContainerDqwcu)
 * 点击查询按钮，需要和
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerDqwcuOnClickClient(rc) {
    var timeRange = Artery.get('atyTimePickerSvpcy').getValue();
    if(timeRange === ""){
        Artery.alert.warning("请正确的选择时间范围");
        return
    }
    var range = timeRange.split(",");
    if(range.length !== 2){
        Artery.alert.warning("请正确的选择时间的范围");
        return;
    }
    startTime = range[0];
    endTime = range[1];

    getCheckboxOptions(rc, startTime, endTime, 'zlnr');
}



/**
 * 点击时触发客户端脚本(atyContainerDqwcu1)
 * 点击查询按钮，需要和
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerDqwcu1OnClickClient(rc) {
    var timeRange = Artery.get('atyTimePickerSvpcy1').getValue();
    if(timeRange === ""){
        Artery.alert.warning("请正确的选择时间范围");
        return
    }
    var range = timeRange.split(",");
    if(range.length !== 2){
        Artery.alert.warning("请正确的选择时间的范围");
        return;
    }
    startTime = range[0];
    endTime = range[1];

    getCheckboxOptions(rc, startTime, endTime, "scnr", undefined, Artery.get('atyInputSearch').getValue());
}



/**
 * 点击时触发客户端脚本(atyContainerDqwcu1)
 * 点击查询按钮，需要和
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerDqwcu2OnClickClient(rc) {
     Artery.get('atyContainerOfkbc1').hide()
}



// 从消息盒子里获取到消息列表
function getCheckboxOptions(rc, startTime, endTime, type, idsArray, queryString){
    var timeerid = 'atyTimePickerSvpcy'
    var compontid = 'atyContainerPniqw'
    var liebiao = 'atyContainerLcliv'
    var checkbox = 'atyCheckboxDebgu'
    if(type === 'scnr'){
        timeerid = 'atyTimePickerSvpcy1'
        compontid = 'atyContainerPniqw1'
        liebiao = 'atyContainerLcliv1'
        checkbox = 'atyCheckboxDebgu1'
    }


    Artery.get(timeerid).setValue(startTime + " , " + endTime);
    Artery.parseComponent({
        data: {
            componentId: compontid,
            startTime: startTime,
            endTime: endTime,
            query: queryString,
            loginId: loginId,
            type: type
        },
        scope: this,
        async: true,
        callback: function(result, jqXHR, textStatus) {
            Artery.get(liebiao).removeAll();
            Artery.get(liebiao).addParsedComponent(result);

            if(idsArray !== undefined && idsArray.length > 0){
                let id_new = ""
                var ckb = $("#atyCheckboxDebgu .aty-checkbox-input")
                if(type === 'scnr'){
                    ckb = $("#atyCheckboxDebgu1 .aty-checkbox-input")
                }
                ckb.each(function (){
                    let value = $(this).attr("value")
                    for(var i = 0; i < idsArray.length; i++){
                        if(value === idsArray[i] && idsArray[i] !== ""){
                            id_new += (value + ",")
                        }
                    }
                    let spandiv = $(this).parent().find('.aty-checkbox-span')
                    if(spandiv !== undefined){
                        let ct = spandiv[0].textContent;
                        $(spandiv[0]).attr("title", ct)
                    }
                });
                Artery.get(checkbox).setValue(id_new);
            }
        }
    });
}


function setzlxxHieght(){
    var box = document.getElementById("atyContainerOfkbc")
    var btn = document.getElementById("atyContainerKgkrp")
    var list = document.getElementById("atyContainerLcliv")
    box.style.height = (btn.offsetTop - 33) + "px";
    list.style.height = (btn.offsetTop - 86) + "px";
    list.style.overflow = "auto";
}

function setzlxxHieght1(){
    var box = document.getElementById("atyContainerOfkbc1")
    var btn = document.getElementById("atyContainerKgkrp1")
    var list = document.getElementById("atyContainerLcliv1")
    box.style.height = (btn.offsetTop - 33) + "px";
    list.style.height = (btn.offsetTop - 140) + "px";
    list.style.overflow = "auto";
}

window.onresize = function (){
    // setzlxxHieght()
    // setzlxxHieght1()
}
/**
 * 点击时触发客户端脚本(atyContainerDdcwu)
 * 点击全选的操作
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerDdcwuOnClickClient(rc) {
    let ids = ""
    $("#atyCheckboxDebgu .aty-checkbox-input").each(function (){
        let value = $(this).attr("value")
        ids += (value + ",");
    })
    Artery.get("atyCheckboxDebgu").setValue(ids);
}

/**
 * 点击时触发客户端脚本(atyContainerDdcwu1)
 * 点击全选的操作
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerDdcwu1OnClickClient(rc) {
    let ids = ""
    $("#atyCheckboxDebgu1 .aty-checkbox-input").each(function (){
        let value = $(this).attr("value")
        ids += (value + ",");
    })
    Artery.get("atyCheckboxDebgu1").setValue(ids);
}


/**
 * 上传文件完成时脚本(atyUploadAejkv)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  file 上传成功的文件对象，类型为File对象
 */
function atyUploadAejkvOnDoneClient (rc, file){
    $('.icon-file-loading').show()
}

/**
 * 上传文件失败时脚本(atyUploadAejkv)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  file 上传失败的文件对象，类型为File对象
 */
function atyUploadAejkvOnFailClient (rc, file){

}
/**
 * 点击时脚本(atyButtonWebPb1)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonWebPb1OnClickClient(rc) {
    if(window.parent.format) {
        window.parent.format();
    }
}

/**
 * 点击时脚本(atyButtonWebPb2)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonWebPb2OnClickClient(rc) {
    if(window.parent.format) {
        window.parent.format();
    }
}

/**
 * 点击时脚本(atyButtonWebPb3)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonWebPb3OnClickClient(rc) {
    if(window.parent.format) {
        window.parent.format();
    }
}
/**
 * 值改变时脚本(atyCheckboxshshs)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  newValue 控件改变后的新值
 * @param  oldValue 控件改变前的旧值
 */
function atyCheckboxshshsOnChangeClient (rc, newValue, oldValue){
    Artery.request({
        url: 'client/fdqc/updateDwpz',
        type: 'POST',
        contentType: 'application/x-www-form-urlencoded; charset=UTF-8',
        timeout: 60000,
        data: {
            "value": newValue
        },
        success: function(data, textStatus, response, options) {
            console.log(data)
        },
        error: function (jqXHR, textStatus, errorThrown) {
        },
        complete:  function (jqXHR, textStatus) {
        }
    })
}

function removeAllBtnStyle(){
    $('#atyButtonElyux').removeClass('zsbtn-active');
    $('#atyButtonDuopp').removeClass('zsbtn-active');
    $('#atyButtonRblns').removeClass('zsbtn-active');
    $('#atyButtonHzanm').removeClass('zsbtn-active');
}

function restoreZskz(){
    Artery.get('atyContainerIaiok').hide()
    Artery.get('atyContainerIaiow').hide()
    $('#atyTextLucvl').removeClass('title-float-left-nt')
    $('#atyContainerKgkrp').removeClass('title-float-left-nt')
    $('#atyContainerKgkrp1').removeClass('title-float-left-nt')
    zskz = {
        'actnum': -1,  // 0 1 2 3 按钮的顺序
        'zs': -1,  //激活的字数
    }
}
/**
 * 点击时脚本(atyButtonElyux)
 * 短文
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonElyuxOnClickClient(rc) {
    // 其他样式删除 删除 atyButtonDuopp  atyButtonRblns  atyButtonHzanm
    let flag = false;
    if($('#atyButtonElyux').hasClass('zsbtn-active')){
        flag = true
    }
    removeAllBtnStyle()
    // 追加样式
    if(flag){
        restoreZskz()
    }else{
        $('#atyButtonElyux').addClass('zsbtn-active');
        // 数据修改
        zskz = {
            'actnum': 0,  // 0 1 2 3 按钮的顺序
            'zs': 600,  //激活的字数
        }
    }
}

/**
 * 点击时脚本(atyButtonDuopp)
 * 中
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonDuoppOnClickClient(rc) {
// 其他样式删除 删除 atyButtonDuopp  atyButtonRblns  atyButtonHzanm
    let flag = false;
    if($('#atyButtonDuopp').hasClass('zsbtn-active')){
        flag = true
    }
    removeAllBtnStyle()
    // 追加样式
    if(flag){
        restoreZskz()
    }else{
        $('#atyButtonDuopp').addClass('zsbtn-active');
        // 数据修改
        zskz = {
            'actnum': 1,  // 0 1 2 3 按钮的顺序
            'zs': 1200,  //激活的字数
        }
    }
}
/**
 * 点击时脚本(atyButtonRblns)
 * 长
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonRblnsOnClickClient(rc) {
    let flag = false;
    if($('#atyButtonRblns').hasClass('zsbtn-active')){
        flag = true
    }
    removeAllBtnStyle()
    // 追加样式
    if(flag){
        restoreZskz()
    }else{
        $('#atyButtonRblns').addClass('zsbtn-active');
        // 数据修改
        zskz = {
            'actnum': 2,  // 0 1 2 3 按钮的顺序
            'zs': 2000,  //激活的字数
        }
    }
}
/**
 * 点击时触发客户端脚本(atyContainerRhcce)
 * 3000字
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerRhcceOnClickClient(rc) {
    removeAllBtnStyle()
    // 追加样式
    if($('#atyContainerRhcce').hasClass('zsbtn-active')){
        restoreZskz()
        Artery.get('atyButtonHzanm').setText('...')
        Artery.get('tooltipasy').disable()
    }else{
        $('#atyButtonHzanm').addClass('zsbtn-active');
        // 数据修改
        zskz = {
            'actnum': 3,  // 0 1 2 3 按钮的顺序
            'zs': 3000,  //激活的字数
        }
        Artery.get('atyButtonHzanm').setText('3K')
        Artery.get('tooltipasy').enable();
        Artery.get('tooltipasy').setContent('3000字')
        // 这里显示提示：
        $('#atyTextLucvl').addClass('title-float-left-nt')
        $('#atyContainerKgkrp').addClass('title-float-left-nt')
        $('#atyContainerKgkrp1').addClass('title-float-left-nt')
        Artery.get('atyContainerIaiok').show();
        Artery.get('atyContainerIaiow').hide();
    }
}
/**
 * 点击时触发客户端脚本(atyContainerWdvcq)
 * 5000字
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerWdvcqOnClickClient(rc) {
    removeAllBtnStyle()

    if($('#atyContainerWdvcq').hasClass('zsbtn-active')){
        restoreZskz()
        Artery.get('atyButtonHzanm').setText('...')
        Artery.get('tooltipasy').disable()
    }else{
        // 追加样式
        $('#atyButtonHzanm').addClass('zsbtn-active');
        // 数据修改
        zskz = {
            'actnum': 3,  // 0 1 2 3 按钮的顺序
            'zs': 5000,  //激活的字数
        }
        Artery.get('atyButtonHzanm').setText('5K')
        Artery.get('tooltipasy').enable();
        Artery.get('tooltipasy').setContent('5000字')
        Artery.get('atyContainerIaiok').show();
        Artery.get('atyContainerIaiow').hide()
        $('#atyTextLucvl').addClass('title-float-left-nt')
        $('#atyContainerKgkrp').addClass('title-float-left-nt')
        $('#atyContainerKgkrp1').addClass('title-float-left-nt')
    }
}


/**
 * 点击时触发客户端脚本(atyContainerWdvyw)
 * 10000字
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerWdvywOnClickClient(rc) {
    removeAllBtnStyle()

    if($('#atyContainerWdvcq').hasClass('zsbtn-active')){
        restoreZskz()
        Artery.get('atyButtonHzanm').setText('...')
        Artery.get('tooltipasy').disable()
    }else{
        // 追加样式
        $('#atyButtonHzanm').addClass('zsbtn-active');
        // 数据修改
        zskz = {
            'actnum': 3,  // 0 1 2 3 按钮的顺序
            'zs': 10000,  //激活的字数
        }
        Artery.get('atyButtonHzanm').setText('10K')
        Artery.get('tooltipasy').enable();
        Artery.get('tooltipasy').setContent('10000字')
        Artery.get('atyContainerIaiok').hide();
        Artery.get('atyContainerIaiow').show();
        $('#atyTextLucvl').addClass('title-float-left-nt')
        $('#atyContainerKgkrp').addClass('title-float-left-nt')
        $('#atyContainerKgkrp1').addClass('title-float-left-nt')
    }
}

/**
 * 点击时脚本(atyButtonHzanm)
 * 其他字
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonHzanmOnClickClient(rc) {
    if ($('#atyContainerPthbv').is(':visible')) {
        Artery.get('atyContainerPthbv').hide()
    }else{
        Artery.get('atyContainerPthbv').show()
        $('#atyContainerRhcce').removeClass('zsbtn-active')
        $('#atyContainerWdvcq').removeClass('zsbtn-active')
        if(zskz.zs === 3000){
            $('#atyContainerRhcce').addClass('zsbtn-active')
        }
        if(zskz.zs === 5000){
            $('#atyContainerWdvcq').addClass('zsbtn-active')
        }
    }
}

function updateWzcdState() {
    var gwwz = selectedGwwzId.length === 0 ? "" : Artery.get(selectedGwwzId).el.text();
    var flag1 = Artery.get('atyRadioOkcxn').getValue() === 'gov' && (gwwz === '决议' || gwwz === '议案' || gwwz === '报告' || gwwz === '通报');
    var flag2 = Artery.get('atyRadioOkcxn').getValue() === 'other';
    if(flag1 || flag2) {
        console.log("可以写长文本");
        // Artery.get('atyButtonHzanm').setText('...')
        // Artery.get('tooltipasy').disable()
        // $('#atyButtonHzanm').removeClass('zsbtn-active');
        // Artery.get('tooltipasy').show();
    }else {
        if(zskz.zs == 3000 || zskz.zs == 5000 ){
            restoreZskz();
        }
        Artery.get('tooltipasy').hide();
    }
}


/**
 * 点击时脚本(atyButtonGkdoi2)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonGkdoi2OnClickClient(rc) {
    if(isWeb) {
        if (window.parent.getEditorContentTextForBj) {
            var rightText = window.parent.getEditorContentTextForBj();
            rc.put("ckgs_path", getCkgs());
            rc.put("ckgs_text", FXCK.fxnr_txt);
            rc.put("right_txt", rightText);
            rc.send(function (result) {
                if(result){
                    openFreamUi(result);
                }
            });
        }
    } else if(!wpsinner) {
        try {
            window.parent.aiWriter.getDocumentContent("", function (resultObj){
                var res = JSON.parse(resultObj);
                if(res.ok){
                    rc.put("ckgs_path", getCkgs());
                    rc.put("right_txt", res.content);
                    rc.send(function (result) {
                        if(result){
                            openFreamUi(result);
                        }
                    });
                }
            })
        }catch (error){
        }
    }else{
        var app = wps_.WpsApplication();
        var doc = app.ActiveDocument;
        if (doc){
            rc.put("ckgs_path", getCkgs());
            rc.put("right_txt",  doc.Range(0, doc.Words.Count).Text);
            rc.send(function (result) {
                if(result){
                    openFreamUi(result);
                }
            });
        }
    }
}



function openFreamUi(cacheId) {
    if(Artery.getTopWindow().getPageData().params.isWeb){
        Artery.open({
            "method": Artery.CONST.HTTP_METHOD.GET,
            "url": 'doc/gwbd?ctype=webCp&openType=web&cacheId='+cacheId,
            "target": "_window",
            "config": {
                modal: true,
                hideFooter: true,
                escClosable: true,
                width: 960,
                height: 700,
                title: '仿写比对',
                iframeId: 'fwxqIframe',
                className: 'fwxqModal',
                onClose: function (iframeId) {

                },
            },
        });
    }else {
        var url = window.location.origin + Artery.completeURL('/doc/gwbd?ctype=webCp&cacheId=' + cacheId);
        if(Artery.getTopWindow().getPageData().params.embed) {
            url = url + "&openType=embed&fontZoom="+Artery.getTopWindow().getPageData().params.fontZoom;
            if(compareVersions('3.1.1.1', Artery.getTopWindow().getPageData().params.version) == -1) {
                window.parent.parent.aiWriter.openWebViewer(JSON.stringify({'url': url, 'windowTitle':'仿写比对', 'windowWidth':900, 'windowHeight':600}));
            }else {
                window.parent.parent.aiWriter.openWebViewer(url);
            }
        }else {
            url = url + "&openType=wps&ctype=webCp&fontZoom="+Artery.getTopWindow().getPageData().params.fontZoom + '&cacheId=' + cacheId;
            Artery.getParentWindow().wps.ShowDialog(url, "仿写比对", 900, 600, true, false);
        }
    }
}



function compareVersions(version1, version2) {
    if(version2 === undefined){
        return 0;
    }
    let v1 = version1.split(".");
    let v2 = version2.split(".");

    for (let i = 0; i < v1.length; i++) {
        if (parseInt(v1[i]) > parseInt(v2[i])) return 1;
        if (parseInt(v1[i]) < parseInt(v2[i])) return -1;
    }

    if (v1.length < v2.length) return -1;
    if (v1.length > v2.length) return 1;

    return 0;
}
/**
 * 点击时脚本(atyButtonUdlpm)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonUdlpmOnClickClient(rc) {
    var ckp = getCkgs();
    if(ckp !== "" || FXCK.fxnr_id !== ""){
        Artery.alert.warning('已经有参考内容，可在清空仿写材料列表后重新选择')
        return
    }
    Artery.get('atyModalLhhby').show()
    handleSearch(rc)
}
/**
 * 添加文件前脚本(atyUploadAejkv1)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  files 选中的文件对象，类型为File对象数组
 */
function atyUploadAejkv1OnBeforeAddClient (rc, files){
    if(FXCK.fxnr_id !== ""){
        Artery.alert.warning('已经有参考内容，可在清空仿写材料列表后重新选择')
        if(Artery.get('atyContainerGengj')) {
            Artery.get('atyContainerGengj').hide();
        }
        return false
    }
    return true;
}


/**
 * 添加文件前脚本(atyUploadAejkv1)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function handleSearch(rc){
    var params = {
        "limit":5,
        "offset":0
    }
    handlePagingBarChange(params, true)
}
/**
 * 分页栏改变时脚本(pageingbar)
 *
 * @param  params 分页传递的参数，如:{offset:0,limit:10}
 */
function handlePagingBarChange(params, reload){
    //params = Object.assign(params, Artery.get("searchForm").getData())

    var searchKey = Artery.get("atysearchInput").getValue()
    if(searchKey !== "" && searchKey !== undefined)
    params["keyword"] = searchKey
    Artery.get("atyLoopAreaVmhvf").reload({
        // 自定义参数
        params: params,
        // 回调
        callback: function (response) {
            if (reload) {
                Artery.get("pageingbar12").reload(response.total)
            }
        }
    })
}
/**
 * 点击时触发客户端脚本(atyContainertitle)
 *
 * @param rc 系统提供的AJAX调用对象
 * @param {rs1.id}
 * @param {rs1.detailUrl}
 */
function atyContainertitleOnClickClient(rc, id, detailUrl) {
    if(detailUrl !== undefined && detailUrl !== ""){
        if (detailUrl === "zcdt") {
            var data = {"id": id};
            Artery.open({
                "url": "web/zx/ztdcxq",
                "data": data,
                target: Artery.CONST.URL_TARGET.BLANK
            });
            return;
        }
        if (detailUrl === "snpt") {
            var data = {"id": id};
            Artery.open({
                "url": "web/tpl/modulexq",
                "data": data,
                target: Artery.CONST.URL_TARGET.BLANK
            });
            return;
        }
        if(Artery.getParentWindow().getPageData().params.isWeb){
            window.open(detailUrl);
        }else
        if(Artery.getParentWindow().getPageData().params.embed) {
            window.parent.smartWriter.openUrl(detailUrl);
        }else {
            let ws = Artery.getParentWindow().wps === undefined? Artery.getParentWindow().parent.wps: Artery.getParentWindow().wps;
            var pageObj = ws.TabPages;
            if(pageObj.hasOwnProperty('OpenWebUrl')){
                pageObj.OpenWebUrl(detailUrl);
            } else {
                pageObj.Add(detailUrl);
            }
        }
        return
    }

    if(Artery.getTopWindow().getPageData().params.isWeb){
        const _this = this;
        Artery.open({
            "url": "web/fw/fwxq",
            "target": "_window",
            "config": {
                modal: true,
                hideFooter: true,
                escClosable: false,
                width: 900,
                height: 600,
                title: '范文详情',
                iframeId: 'fwxqIframe',
                className: 'fwxqModal',
                onClose: function (iframeId) {
                    Artery.request({
                        url: 'web/fw/fwxq/onLoadServer',
                        data: {
                            id: id
                        },
                        success: function(data, textStatus, response, options) {
                            if(data.sctype === '1') {
                                var scButtonIcon = _this.el.find('.fwsc-button').find("i")[0];
                                scButtonIcon.classList.remove('fwsc-icon');
                                scButtonIcon.classList.add('fwsc-icon-active');
                            }
                            if(data.sctype === '0') {
                                var scButtonIcon = _this.el.find('.fwsc-button').find("i")[0];
                                scButtonIcon.classList.remove('fwsc-icon-active');
                                scButtonIcon.classList.add('fwsc-icon');
                            }
                        },
                        error: function (jqXHR, textStatus, errorThrown) {
                            console.log(jqXHR, textStatus, errorThrown);
                        }
                    });
                }
            },
            "data": {
                "id": id,
                "openType": 'web'
            }
        });
    }else {
        // 插件打开的时候是个新窗口了，sessionId会变，所以需要重新做下单点，否则后台获取用户信息为空
        var url = window.location.origin + Artery.completeURL("/web/fw/fwxq?id="+id+"&userInfo="+Artery.params.userInfo)+ssodata;
        if(Artery.getTopWindow().getPageData().params.embed) {
            url = url + "&openType=embed&fontZoom="+Artery.getTopWindow().getPageData().params.fontZoom;
            if(compareVersions('3.1.1.1', Artery.getTopWindow().getPageData().params.version) == -1) {
                window.parent.parent.smartWriter.openWebViewer(JSON.stringify({'url': url, 'windowTitle':'范文详情', 'windowWidth':900, 'windowHeight':600}));
            }else {
                window.parent.parent.smartWriter.openWebViewer(url);
            }
        }else {
            url = url + "&openType=wps&fontZoom="+Artery.getTopWindow().getPageData().params.fontZoom;
            Artery.getParentWindow().wps.ShowDialog(url, "范文详情", 900, 600, true, false);
        }
    }



    // rc.put("param1", "param1_value");
    // rc.send(function(result) {
    // });
}
/**
 * 点击时脚本(atyButtonZjkrn)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  @param {rs1.id}
 * @param  @param {rs1.text}
 * @param  @param {rs1.title}
 */
function atyButtonZjkrnOnClickClient(rc, id, text, title) {
    // 选中按钮
    FXCK.fxnr_id = id;
    FXCK.fxnr_txt = text;
    FXCK.fxnr_title = title;


    var div = document.createElement("div");
    div.className = "container-file";
    div.id = id;
    var icon_file = document.createElement("i");
    icon_file.className = "icon-file";
    var span = document.createElement("span");
    span.className = "text-filename";
    span.innerText = title;
    span.title = title;
    var icon_del = document.createElement("i");
    icon_del.className = "icon-file-del";
    icon_del.onclick = function () {
        document.getElementById("atyContainerJslmk").removeChild(div);
        FXCK.fxnr_id = "";
        FXCK.fxnr_txt = "";
        FXCK.fxnr_title = "";
    }
    var icon_loading = document.createElement("i");
    icon_loading.className = "icon-file-loading";
    icon_loading.id = "icon-file-loading";
    div.append(icon_file);
    div.append(span);
    div.append(icon_del);
    div.append(icon_loading);
    document.getElementById("atyContainerJslmk").append(div);

    Artery.get("atyModalLhhby").hide()

}
/**
 * 点击控件时脚本()
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function closeThinkModel (rc){
    Artery.get('atyDragdiv').hide()
}
/**
 * 点击时触发客户端脚本()
 * 展开或者收起思考过程
 * @param rc 系统提供的AJAX调用对象
 */
function showOrCloseThinkStep(rc) {
    const divElement = document.getElementById('atyTextTs');
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        Artery.get('atycontainerTc').hide()
        // $('#atyContainerDker').css('width', 'fit-content');
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        Artery.get('atycontainerTc').show()
        // $('#atyContainerDker').css('width', '100%');
    }
}


/**
 * 点击时触发客户端脚本()
 * 展开或者收起思考过程
 * @param rc 系统提供的AJAX调用对象
 */
function showOrCloseAbsThinkStep(rc){
    const divElement = document.getElementById('atyTextAbsTs');
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        Artery.get('atycontainerTcAbs').hide()
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        Artery.get('atycontainerTcAbs').show()
    }
}

/**
 * 点击时触发客户端脚本()
 * 展开或者收起思考过程
 * @param rc 系统提供的AJAX调用对象
 */
function showOrCloseOutThinkStep(rc){
    const divElement = document.getElementById('atyTextOutTs');
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        Artery.get('atycontainerTcOut').hide()
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        Artery.get('atycontainerTcOut').show()
    }
}


/**
 * 点击时触发客户端脚本()
 * 展开或者收起思考过程
 * @param rc 系统提供的AJAX调用对象
 */
function showOrCloseOutThinkStepout(rc){
    document.getElementById("atyTextOutTs").click();
    const divElement = document.getElementById('atyTextOutTsout');
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        Artery.get('atycontainerTcOutout').hide()
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        Artery.get('atycontainerTcOutout').show()
    }
}


/**
 * 点击时触发客户端脚本()
 * 展开或者收起思考过程
 * @param rc 系统提供的AJAX调用对象
 */
function showOrCloseAbsThinkStepabs(rc){
    const divElement = document.getElementById('atyTextAbsTsabs');
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        Artery.get('atycontainerTcAbsabs').hide()
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        Artery.get('atycontainerTcAbsabs').show()
    }
}

function scrollToBottom(e) {
    if(_scrollToBottom) {
        e.scrollTop = e.scrollHeight;
    }
}
/**
 * 点击时脚本()
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function showRoleModel(rc) {
    Artery.get("atyModelRole").show()
}
/**
 * 点击确认按钮时脚本(atyModelRole)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyModelRoleOnOkClient (rc){
    var content = Artery.get('atyTextAreaRole').getValue()
    rc.put("roleContent", content);
    rc.send(function(result) {
        Artery.get('atyModelRole').hide()
    });
}


/**
 * 点击时脚本(atyButtonUxpqc)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonUxpqcOnClickClient(rc) {
    onlinesearch = !onlinesearch
    var btn = document.getElementById("atyButtonUxpqc");
    if(btn.classList.contains('btn-zskss-sel')) {
        btn.classList.remove('btn-zskss-sel');
    }else {
        btn.classList.add('btn-zskss-sel');
    }
}

/**
 * 点击时触发客户端脚本(atyContainerCgocm)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerCgocmOnClickClient(rc) {
    const divElement = rc.component.el[0];
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        document.querySelector("[id^=\"atyContainerHnimx\"]").style.display = 'none';
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        document.querySelector("[id^=\"atyContainerHnimx\"]").style.display = 'block';
    }
}

/**
 * 点击时触发客户端脚本(atyContainerMfpkc)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerMfpkcOnClickClient(rc) {
    const divElement = rc.component.el[0];
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        document.querySelector("[id^=\"atyContainerAkgda\"]").style.display = 'none';
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        document.querySelector("[id^=\"atyContainerAkgda\"]").style.display = 'block';
    }
}

/**
 * 点击时触发客户端脚本(atyContainerDgtdq)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerDgtdqOnClickClient(rc) {
    const divElement = rc.component.el[0];
    const hasClass = divElement.classList.contains('close-think-step');
    if(hasClass){
        divElement.classList.remove("close-think-step")
        divElement.classList.add("show-think-step")
        // 关闭
        document.querySelector("[id^=\"atyContainerMhozh\"]").style.display = 'none';
    }else{
        divElement.classList.add("close-think-step")
        divElement.classList.remove("show-think-step")
        document.querySelector("[id^=\"atyContainerMhozh\"]").style.display = 'block';
    }
}

//新版页面开始================================================================

function setZksqStatus(e, c) {
    if(e.classList.contains('expand')) {
        e.classList.remove('expand');
        e.classList.add('collapse');
        if(c) {
            c.show();
        }
    }else {
        e.classList.remove('collapse');
        e.classList.add('expand');
        if(c) {
            c.hide();
        }
        $('#atyCheckBoxzskcom').hide();
    }
}

/**
 * 大纲展开收起
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerZouxwOnClickClient(rc) {
    setZksqStatus(Artery.get('atyContainerRgvlh').el[0], Artery.get('atyTextareaEjhta'));
}

/**
 * 添加结构参考展开收起
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerJefztOnClickClient(rc) {
    setZksqStatus(Artery.get('atyContainerCposp').el[0], Artery.get('atyContainerUglxh'));
}

/**
 * 点击时触发客户端脚本(atyContainerHzgjm)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerHzgjmOnClickClient(rc) {
    setZksqStatus(Artery.get('atyContainerDzfpk').el[0], Artery.get('atyContainerQsrna'));
}


/**
 * 点击时触发客户端脚本(atyContainerHzgjm)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerHzgjmhmdOnClickClient(rc) {
    setZksqStatus(Artery.get('atyContainerDzfpkhmd').el[0], Artery.get('atyContainerQsrnahmd'));
}

/**
 * 点击时触发客户端脚本(atyContainerEtrak)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerEtrakOnClickClient(rc) {
    onlinesearch = !onlinesearch
    var zskssElement = rc.component.el[0];
    var xzwgElement = Artery.get('atyContainerKervu').el[0];
    if(zskssElement.classList.contains('sel')) {
        zskssElement.classList.remove('sel');
        xzwgElement.classList.add('sel');
        Artery.get('atyIconKrivz1').hide();
        // 隐藏一下引用参考的弹窗
        $('#atyCheckBoxzskcom').hide();
    }else {
        zskssElement.classList.add('sel');
        xzwgElement.classList.remove('sel');
        Artery.get('atyIconKrivz1').show();
    }
    document.getElementById("atyContainerEdvxu").innerHTML = '';
    Object.keys(_files).map(function (filename) {
        if(filename.split('_')[0] === 'cknr'){
            delete _files[filename]
        }
    })
}

/**
 * 点击时触发客户端脚本(atyContainerKervu)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerKervuOnClickClient(rc) {
    if(rc.component.el[0].classList.contains('sel')) {
        Artery.get('atyContainerQpucs').show();
    }
}
/**
 * 点击时触发客户端脚本(atyContainerDiett)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerDiettOnClickClient(rc) {
    Artery.get('atyContainerQpucs').hide();
    var dialog = wps_.Application.FileDialog(3);
    dialog.Filters.Add("doc文档和docx文档和wps文档和txt文档和pdf文档", "*.doc;*.docx;*.wps;*.txt;*.pdf", 1);
    dialog.AllowMultiSelect = true;//单选，true为多选
    if(dialog.Show()) {
        dialog.Execute();
        var duplicateName = false;
        for(var i = 1; i <= dialog.SelectedItems.Count; i++) {
            // 判断超出文件数量限制
            if(Object.keys(_files).length + Object.keys(_xcfiles).length === 10) {
                Artery.alert.warning("文件个数超出最大限制");
                return;
            }
            var filepath = dialog.SelectedItems.Item(i);
            var filename = '';
            if(!filepath.startsWith('/')) {
                // js版插件windows上获取的文件路径分隔符有/也有\，\会被当成转义字符，所以做以下处理，String.raw表示忽略转义字符的含义按原始内容输出
                filepath = String.raw`${dialog.SelectedItems.Item(i)}`;
                var filepathSplit = filepath.split(/[/\\]/);
                filename = filepathSplit[filepathSplit.length - 1];
            }else {
                var filepathSplit = filepath.split(/[/\\]/);
                filename = filepathSplit[filepathSplit.length - 1];
            }
            if("cknr_" + filename in _xcfiles) {
                duplicateName = true;
            }else {
                _xcfiles["cknr_" + filename] = filepath;

                var div = document.createElement("div");
                div.className = "container-file";
                div.id = getUuid();

                var icon_file = document.createElement("i");
                icon_file.className = "icon-file";

                var span = document.createElement("span");
                span.className = "text-filename";
                span.innerText = filename;
                span.title = filename;

                var icon_del = document.createElement("i");
                icon_del.className = "icon-file-del";
                icon_del.setAttribute("data-filename", filename);
                icon_del.onclick = function (e) {
                    var deldiv = e.target.parentNode;
                    var delname = e.target.dataset.filename;
                    document.getElementById("atyContainerEdvxu").removeChild(deldiv);
                    delete _xcfiles["cknr_" + delname];
                }

                div.append(icon_file);
                div.append(span);
                div.append(icon_del);
                document.getElementById("atyContainerEdvxu").append(div);
            }
        }
        if(duplicateName) {
            Artery.alert.warning("不可上传同名文件");
        }
    }
}

/**
 * 点击时触发客户端脚本(atyContainerQueox)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerQueoxOnClickClient(rc) {
    showYysczl('scnr');
}


/**
 * 点击时触发客户端脚本(atyContainerQueox)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerQueoxhmdOnClickClient(rc) {
    if(Artery.get('switchChangeZsss').getValue()){
        return;
    }
    showYysczl('hmd');
}



/**
 * 点击时触发客户端脚本(atyContainerVifwo)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerVifwoOnClickClient(rc) {
    showYysczl('zlnr');
}

function showYysczl(type) {
    if(type === "zlnr") {
        Artery.get('atyModalBkimt').setTitle("引用摘要");
    } else if(type === "hmd") {
        Artery.get('atyModalBkimt').setTitle("个人文稿");
    } else if("scnr") {
        Artery.get('atyModalBkimt').setTitle("引用收藏");
    }
    Artery.get('atyModalBkimt').show();
    if(Artery.get('atyContainerQpucs')){
        Artery.get('atyContainerQpucs').hide();
    }

    Artery.get('atyInputGowub').setValue('');
    Artery.get('atyHiddenBczlo').setValue(type);

    // TJNRCK.sc = [];
    // TJNRCK.zl = [];

    reloadYysczl(0);
}

function reloadYysczl(offset) {
    var type = Artery.get('atyHiddenBczlo').getValue();
    Artery.parseComponent({
        data: {
            componentId: 'atyContainerFsflm',
            query: Artery.get('atyInputGowub').getValue(),
            isWeb: isWeb,
            loginId: loginId,
            type: type,
            offset: offset
        },
        scope: this,
        async: true,
        callback: function(result, jqXHR, textStatus) {
            Artery.get('atyContainerBknht').removeAll();
            Artery.get('atyContainerBknht').addParsedComponent(result);

            if(type === 'scnr' && TJNRCK.sc.length > 0) {
                TJNRCK.sc.forEach(function (value) {
                    var e = document.querySelector('.result-yysczl-list input[value="' + value.id + '"]');
                    if(e) {
                        e.parentElement.classList.add('sel');
                    }
                })
            }
            if(type === 'zlnr' && TJNRCK.zl.length > 0) {
                TJNRCK.sc.forEach(function (value) {
                    var e = document.querySelector('.result-yysczl-list input[value="' + value.id + '"]');
                    if(e) {
                        e.parentElement.classList.add('sel');
                    }
                })
            }
        }
    });
}

/**
 * 分页栏改变时脚本(atyPagingbarSoqnf)
 *
 * @param  params 分页传递的参数，如:{offset:0,limit:10}
 */
function atyPagingbarSoqnfOnChangeClient (params){
    reloadYysczl(params.offset);
}

/**
 * 点击时触发客户端脚本(atyContainerChgbd)
 *
 * @param rc 系统提供的AJAX调用对象
 * @param {rs3.value}
 * @param {rs3.text}
 */
function atyContainerChgbdOnClickClient(rc, value, text) {
    var type = Artery.get('atyHiddenBczlo').getValue();
    var e = rc.component.el[0];
    var check = !e.classList.contains('sel');
    if(check) {
        e.classList.add('sel');
        if(type === 'scnr' || type === 'hmd') {
            TJNRCK.sc.push({id: value, text: text});
        }
        if(type === 'zlnr') {
            TJNRCK.zl.push({id: value, text: text});
        }
    }else {
        e.classList.remove('sel');
        if(type === 'scnr') {
            TJNRCK.sc.splice(TJNRCK.sc.indexOf({id: value, text: text}), 1);
        }
        if(type === 'zlnr') {
            TJNRCK.zl.splice(TJNRCK.zl.indexOf({id: value, text: text}), 1);
        }
    }
}

/**
 * 自定义验证时脚本(atyTextareaEjhta)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  value 进行校验的值
 */
function atyTextareaEjhtaOnValidClient (rc, value){
    if(value.length > 0) {
        var xh1 = false;
        var xh2 = false;
        var arr = value.split('\n');
        arr.forEach(function (str) {
            if(str.startsWith('一、')) {
                xh1 = true;
            }
            if(xh1 && str.startsWith('二、')) {
                xh2 = true;
            }
        });
        if(xh1 && xh2) {
            return true;
        }
    }else {
        return true;
    }
    Artery.notice.warning({
        title: '填写大纲不规范',
        desc: '至少需包含两个以上一级标题'
    });
    return "";
}

/**
 * 尾部图标点击时脚本(atyInputGowub)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyInputGowubOnIconClickClient (rc){
    // TJNRCK.sc = [];
    // TJNRCK.zl = [];

    reloadYysczl(0);
}

/**
 * 点击时脚本(atyButtonDugjp)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyButtonDugjpOnClickClient(rc) {
    Artery.get('atyModalBkimt').hide();

    var type = Artery.get('atyHiddenBczlo').getValue();
    var datas = (type === 'scnr' || type === 'hmd') ? TJNRCK.sc : TJNRCK.zl;
    var className = type === 'scnr' ? "icon-file-yysczl-sc" : 'icon-file-yysczl-zl';

    datas.forEach(function (data) {
        if(document.getElementById(data.id) !== null) {
            return;
        }
        // 生成文件列表
        var div = document.createElement("div");
        div.className = "container-file";
        div.id = data.id;
        var icon_file = document.createElement("i");
        icon_file.className = className;
        var span = document.createElement("span");
        span.className = "text-filename";
        span.innerText = data.text;
        span.title = data.text;
        var icon_del = document.createElement("i");
        icon_del.className = "icon-file-del";
        icon_del.onclick = function () {
            if(document.getElementById("atyContainerEdvxu")){
                document.getElementById("atyContainerEdvxu").removeChild(div);
            }
            if(document.getElementById("atyContainerEdvxuhmd")){
                document.getElementById("atyContainerEdvxuhmd").removeChild(div);
            }
            if(type === 'scnr' || type === 'hmd') {
                TJNRCK.sc.splice(TJNRCK.sc.indexOf({id: data.id, text: data.text}), 1);
            }
            if(type === 'zlnr') {
                TJNRCK.zl.splice(TJNRCK.zl.indexOf({id: data.id, text: data.text}), 1);
            }
        }
        var icon_loading = document.createElement("i");
        icon_loading.className = "icon-file-loading";
        icon_loading.id = "icon-file-loading";
        div.append(icon_file);
        div.append(span);
        div.append(icon_del);
        div.append(icon_loading);
        if(Artery.get('atyContainerEdvxu')){
            document.getElementById("atyContainerEdvxu").append(div);
        }
        if(Artery.get('atyContainerEdvxuhmd')){
            document.getElementById("atyContainerEdvxuhmd").append(div);
        }
    });
}

/**
 * 点击时触发客户端脚本(atyContainerYonzk)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerYonzkOnClickClient(rc) {
    var ckp = getCkgs();
    if(ckp !== "" || FXCK.fxnr_id !== ""){
        Artery.alert.warning('已经有参考内容，可在清空仿写材料列表后重新选择')
        return
    }
    Artery.get('atyContainerGengj').show();
}

/**
 * 点击时触发客户端脚本(atyContainerMbmat)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerMbmatOnClickClient(rc) {
    Artery.get('atyContainerGengj').hide();
    if(FXCK.fxnr_id !== ""){
        Artery.alert.warning('已经有参考内容')
        return
    }
    var dialog = wps_.Application.FileDialog(3);
    dialog.Filters.Add("doc文档和docx文档和wps文档和txt文档和pdf文档", "*.doc;*.docx;*.wps;*.txt;*.pdf", 1);
    dialog.AllowMultiSelect = false;//单选，true为多选
    if(dialog.Show()) {
        dialog.Execute();
        var duplicateName = false;
        for(var i = 1; i <= dialog.SelectedItems.Count; i++) {
            // 判断超出文件数量限制
            if(Object.keys(_files).length + Object.keys(_xcfiles).length === 10) {
                Artery.alert.warning("只可上传一个仿写格式文件");
                return;
            }
            var filepath = dialog.SelectedItems.Item(i);
            var filename = '';
            if(!filepath.startsWith('/')) {
                // js版插件windows上获取的文件路径分隔符有/也有\，\会被当成转义字符，所以做以下处理，String.raw表示忽略转义字符的含义按原始内容输出
                filepath = String.raw`${dialog.SelectedItems.Item(i)}`;
                var filepathSplit = filepath.split(/[/\\]/);
                filename = filepathSplit[filepathSplit.length - 1];
            }else {
                var filepathSplit = filepath.split(/[/\\]/);
                filename = filepathSplit[filepathSplit.length - 1];
            }
            if("ckgs_" + filename in _xcfiles || gscontain("ckgs")) {
                duplicateName = true;
            }else {
                _xcfiles["ckgs_" + filename] = filepath;

                var div = document.createElement("div");
                div.className = "container-file";
                div.id = getUuid();

                var icon_file = document.createElement("i");
                icon_file.className = "icon-file";

                var span = document.createElement("span");
                span.className = "text-filename";
                span.innerText = filename;
                span.title = filename;

                var icon_del = document.createElement("i");
                icon_del.className = "icon-file-del";
                icon_del.setAttribute("data-filename", filename);
                icon_del.onclick = function (e) {
                    var deldiv = e.target.parentNode;
                    var delname = e.target.dataset.filename;
                    document.getElementById("atyContainerJslmk").removeChild(deldiv);
                    delete _xcfiles["ckgs_" + delname];
                }

                div.append(icon_file);
                div.append(span);
                div.append(icon_del);
                document.getElementById("atyContainerJslmk").append(div);
            }
        }
        if(duplicateName) {
            Artery.alert.warning("只可上传一个仿写格式文件");
        }
    }
}

/**
 * 点击时触发客户端脚本(atyContainerOysjy)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerOysjyOnClickClient(rc) {
    Artery.get('atyContainerGengj').hide();
    var ckp = getCkgs();
    if(ckp !== "" || FXCK.fxnr_id !== ""){
        Artery.alert.warning('已经有参考内容，可在清空仿写材料列表后重新选择')
        return
    }
    Artery.get('atyModalLhhby').show()
    handleSearch(rc)
}

/**
 * 点击时触发客户端脚本(atyContainerVdsfq)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerVdsfqOnClickClient(rc) {
    Artery.get('atyContainerGengj').hide();
    var ckp = getCkgs();
    if(ckp !== "" || FXCK.fxnr_id !== ""){
        Artery.alert.warning('已经有参考内容，可在清空仿写材料列表后重新选择')
        return
    }
    Artery.get('atyContainerAahle').show();
}

/**
 * 点击时触发客户端脚本(atyContainerBamzb)
 *
 * @param rc 系统提供的AJAX调用对象
 * @param {rs4.id}
 * @param {rs4.template}
 * @param {rs4.title}
 * @param {rs4.subType}
 */
function atyContainerBamzbOnClickClient(rc, id, text, title, subType) {
    Artery.get('atyContainerAahle').hide();

    TJJGCK.tplContent = text;

    if(!title || title.length === 0) {
        title = subType;
    }

    FXCK.fxnr_id = id;
    FXCK.fxnr_txt = text;
    FXCK.fxnr_title = title;

    var div = document.createElement("div");
    div.className = "container-file";
    div.id = id;
    var icon_file = document.createElement("i");
    icon_file.className = "icon-file";
    var span = document.createElement("span");
    span.className = "text-filename";
    span.innerText = title;
    span.title = title;
    var icon_del = document.createElement("i");
    icon_del.className = "icon-file-del";
    icon_del.onclick = function () {
        document.getElementById("atyContainerJslmk").removeChild(div);
        TJJGCK.tplContent = '';
        FXCK.fxnr_id = "";
        FXCK.fxnr_txt = "";
        FXCK.fxnr_title = "";
    }
    var icon_loading = document.createElement("i");
    icon_loading.className = "icon-file-loading";
    icon_loading.id = "icon-file-loading";
    div.append(icon_file);
    div.append(span);
    div.append(icon_del);
    div.append(icon_loading);
    document.getElementById("atyContainerJslmk").append(div);
}

function writeCookie() {
    var docId = '';
    if(window.parent.getDocId) {
        docId = window.parent.getDocId();
    }
    if(docId.length > 0) {
        var key = QC_COOKIE_KEY_PREFIX+docId;
        if ($.cookie(key)) {
            QC_OPTION = JSON.parse($.cookie(key))
        }
        QC_OPTION.gwwz = selectedGwwzId;
        QC_OPTION.title = Artery.get('atyInputYkjao').getValue();
        QC_OPTION.cause = Artery.get('atyTextareaPelma').getValue();
        QC_OPTION.zskz = zskz;
        QC_OPTION.outline = Artery.get('atyTextareaEjhta').getValue();
        $.cookie(key, JSON.stringify(QC_OPTION), {expires: 3, path: '/IntelligentEditor'});
    }
}
function readCookieAndSet() {
    var docId = '';
    if(window.parent.getDocId) {
        docId = window.parent.getDocId();
    }
    if(docId.length > 0) {
        var key = QC_COOKIE_KEY_PREFIX + docId;
        if ($.cookie(key)) {
            QC_OPTION = JSON.parse($.cookie(key))
        }
    }
    Artery.get('atyInputYkjao').setValue(QC_OPTION.title);
    Artery.get('atyTextareaPelma').setValue(QC_OPTION.cause);
    var _selectedGwwzId = QC_OPTION.gwwz;
    if(_selectedGwwzId && _selectedGwwzId.length > 0) {
        var find = false;
        var subComponents = Artery.get("atyToggleLayoutPymgu").subComponents;
        for (var i = 0; i < subComponents.length; i++) {
            if (subComponents[i].id === _selectedGwwzId) {
                find = true;
                break;
            }
        }
        if(!find) {
            Artery.get('atyRadioOkcxn').setValueChangeAndValid('other');
        }
        Artery.get(_selectedGwwzId).click();
    }
    if(QC_OPTION.zskz.zs === 600) {
        Artery.get('atyButtonElyux').click();
    }
    if(QC_OPTION.zskz.zs === 1200) {
        Artery.get('atyButtonDuopp').click();
    }
    if(QC_OPTION.zskz.zs === 2000) {
        Artery.get('atyButtonRblns').click();
    }
    if(QC_OPTION.zskz.zs === 3000) {
        Artery.get('atyContainerRhcce').click();
    }
    if(QC_OPTION.zskz.zs === 5000) {
        Artery.get('atyContainerWdvcq').click();
    }
    if(QC_OPTION.outline && QC_OPTION.outline.length > 0) {
        setZksqStatus(Artery.get('atyContainerRgvlh').el[0], Artery.get('atyTextareaEjhta'));
        Artery.get('atyTextareaEjhta').setValue(QC_OPTION.outline);
    }
}
/**
 * 点击时脚本()
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function evaArticle(rc) {
    var full_text = '';
    var title = Artery.get("atyInputYkjao").getValue();
    if(isWeb){
        full_text = window.parent.getEditorContentText()
        if(!full_text || !title){
            Artery.alert.warning("请填写标题和内容");
            return ;
        }
        window.parent.evaluArticle(full_text, title);
    }else if(wpsinner){
        let app = Artery.getParentWindow().wps.WpsApplication()
        let doc = app.ActiveDocument
        full_text = doc.Range(0, doc.Words.Count).Text;
        if(!full_text || !title){
            Artery.alert.warning("请填写标题和内容");
            return ;
        }
        window.parent.evaluArticle(full_text, title);
    }else{
        window.parent.aiWriter.getDocumentContent("", function (resultObj) {
            var res = JSON.parse(resultObj);
            if (res.ok) {
                full_text = res.content;
                if(!full_text || !title){
                    Artery.alert.warning("请填写标题和内容");
                    return ;
                }
                window.parent.evaluArticle(full_text, title);
            } else {
                Artery.alert.warning("分析上下文内容失败");
            }
        })
    }
}

function rewrite(){
    //从storage 里去处数据
    let dataStr = sessionStorage.getItem('evaReportData') || localStorage.getItem('evaReportData');
    if (!dataStr){
        Artery.alert.warning("未获取到有效的评价数据");
        return;
    }
    evaData = dataStr;
    _continueWriteTime = 0;// 重置持续
    closeDoneButtonGroup()

    if(Artery.get("atyInputYkjao").isValid()){
        if(isWeb){
            if(window.parent.selectWholeText) {
                window.parent.selectWholeText();
            }
            generateDocument("重写")
            writeCookie();
        }else if(wpsinner){
            var app = wps_.WpsApplication();
            var selection = app.Selection;
            selection.WholeStory();
            generateDocument("重写")
            writeCookie();

        }else{
            window.parent.aiWriter.selectAllText();
            generateDocument("重写")
            writeCookie();
        }

    }
}
/**
 * 点击时触发客户端脚本(atyIconKrivz1)
 * 弹出只是搜索的配置框
 * @param rc 系统提供的AJAX调用对象
 */
function atyIconKrivz1OnClickClient(rc) {
    window.event.stopPropagation();
    var $zsksou = $("#atyContainerZsksou");
    if ($zsksou.is(":visible")) {
        $zsksou.hide();
    } else {
        $zsksou.show();
        // 根据hmdRagZsk 的包含的值，来判断是个人知识库还是单位知识库
        if(hmdRagZsk.includes(0)){
            // 个人知识库
            $("#atyIconDxtgesou1").show();
        }
        if(hmdRagZsk.includes(1)){
            // 单位知识库
            $("#atyIconDxtgesou2").show();
        }
        if(hmdRagZsk.includes(2)){
            // 通用知识库
            $("#atyIconDxtgesou3").show();
        }

    }
}
/**
 * 点击时触发客户端脚本(atyContainerVisou1)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerVisou1OnClickClient(rc) {
    //判断是atyIconDxtgesou1状态是隐藏还是显示，如果隐藏，则显示，如果显示，则隐藏
    if($("#atyIconDxtgesou1").is(":hidden")){
        $("#atyIconDxtgesou1").show();
        hmdRagZsk.push(0);
    }else{
        $("#atyIconDxtgesou1").hide();
        hmdRagZsk.splice(hmdRagZsk.indexOf(0), 1);
    }
}


/**
 * 点击时触发客户端脚本(atyContainerVisou1)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerVisou2OnClickClient(rc) {
    //判断是atyIconDxtgesou2状态是隐藏还是显示，如果隐藏，则显示，如果显示，则隐藏
    if($("#atyIconDxtgesou2").is(":hidden")){
        $("#atyIconDxtgesou2").show();
        hmdRagZsk.push(1);
    }else{
        $("#atyIconDxtgesou2").hide();
        hmdRagZsk.splice(hmdRagZsk.indexOf(1), 1);
    }
}

/**
 * 点击时触发客户端脚本(atyContainerVisou1)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyContainerVisou3OnClickClient(rc) {
    //判断是atyIconDxtgesou3状态是隐藏还是显示，如果隐藏，则显示，如果显示，则隐藏
    if($("#atyIconDxtgesou3").is(":hidden")){
        $("#atyIconDxtgesou3").show();
        hmdRagZsk.push(2);
    }else{
        $("#atyIconDxtgesou3").hide();
        hmdRagZsk.splice(hmdRagZsk.indexOf(2), 1);
    }
}

/**
 * 点击时触发客户端脚本(atyContainerVisou1)
 *
 * @param rc 系统提供的AJAX调用对象
 * @Param {rs2.id}
 */
function changeCardStatus(rc, id){
    var $text = $("#atyTextYaufzhan_AcloneA_0_AcloneA_" + id);
    var $icon = $("#atyIconKrivz1k_AcloneA_0_AcloneA_" + id);
    var $cont = $("#atyTextYldlu_AcloneA_0_AcloneA_" + id);
    if ($text.text().trim() === "展开") {
        $text.text("收起");
        $icon.removeClass("icon-article-zk").addClass("icon-article-sq");
        $cont.removeClass("text-zskss-item-content").addClass("text-zskss-item-content-sq");
    } else {
        $text.text("展开");
        $icon.removeClass("icon-article-sq").addClass("icon-article-zk");
        $cont.removeClass("text-zskss-item-content-sq").addClass("text-zskss-item-content");
    }
}
/**
 * 点击控件时脚本(atyTextYaufv1)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  {rs2.url} 系统提供的AJAX调用对象
 *
 */
function atyTextYaufv1downOnClickClient (rc, url){
    if(isWeb){
        window.open(url)
    }else if(wpsinner){
        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.responseType = 'blob';
        xhr.onload = function () {
            if (xhr.status === 200) {
                var blob = xhr.response;
                var reader = new FileReader();
                reader.onload = function (event) {
                    var arrayBuffer = event.target.result;
                    var uint8Array = new Uint8Array(arrayBuffer);
                    var binaryString = String.fromCharCode.apply(null, uint8Array);
                    // 获取文件名，简单处理
                    var fileName = url.split('?')[0].split('/').pop();
                    var tempPath = wps.Env.GetTempPath() + "/" + fileName;
                    wps.FileSystem.writeAsBinaryString(tempPath, binaryString);
                    wps.Application.Documents.Open(tempPath);
                    Artery.alert.warning("正在打开")
                };
                reader.readAsArrayBuffer(blob);
            } else {
                // 建议加错误提示
                Artery.alert.error("下载失败，状态码：" + xhr.status);
            }
        };
        xhr.onerror = function () {
            Artery.alert.error("网络错误，无法下载文件");
        };
        // 可选：设置超时
        xhr.timeout = 30000;
        xhr.ontimeout = function () {
            Artery.alert.error("请求超时");
        };
        xhr.send();
    }else{
        window.parent.aiWriter.openDocumentByUrl(url);
    }
}
/**
 * 点击确认按钮时脚本(atyModalWzts)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyModalWztsOnOkClient (rc){
    window.parent.aiWriter.selectAllText();
    window.parent.aiWriter.deleteSelectedText();
    Artery.get('atyModalWzts').hide();
    generateDocument(selectType)
}



/**
 * 点击取消按钮时脚本(atyModalWzts)
 *
 * @param  rc 系统提供的AJAX调用对象
 */
function atyModalWztsOnCancelClient (rc){
    Artery.get('atyModalWzts').hide();
    generateDocument(selectType)
}
/**
 * 值改变时脚本(switchChangeZsss)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  newValue 控件改变后的新值
 * @param  oldValue 控件改变前的旧值
 */
function switchChangeZsssOnChangeClient (rc, newValue, oldValue){
    var btnIds = ["atybtnsswj", "atybtngrzsk"];
    if(newValue){
        onlinesearch = true;
        Artery.get("atyCheckBoxzsk").show();
        // 禁止编辑，设置按钮样式为灰色并禁用鼠标
        btnIds.forEach(function(id){
            var el = document.getElementById(id);
            if(el){
                el.style.color = "rgba(0,0,0,0.4)";
                el.style.cursor = "not-allowed";
                el.setAttribute("disabled", "disabled");
            }
        });
        // 禁止 .zsss-popup-item .file-upload .aty-upload-button
        var uploadBtns = document.querySelectorAll('.zsss-popup-item .file-upload .aty-upload-button');
        uploadBtns.forEach(function(btn){
            btn.style.color = "rgba(0,0,0,0.4)";
        });
        // 清空id=atyContainerEdvxuhmd的子节点dom
        var container = document.getElementById("atyContainerEdvxuhmd");
        if(container){
            container.innerHTML = "";
        }
        Artery.get('atyuploadbtnsm').show()
        Artery.get('atyUploadAejkv2hmd').hide()
    }else{
        onlinesearch = false;
        Artery.get("atyCheckBoxzsk").hide();
        // 可以编辑，恢复按钮样式
        btnIds.forEach(function(id){
            var el = document.getElementById(id);
            if(el){
                el.style.color = "rgba(0,0,0,1)";
            }
        });
        // 恢复 .zsss-popup-item .file-upload .aty-upload-button
        var uploadBtns = document.querySelectorAll('.zsss-popup-item .file-upload .aty-upload-button');
        uploadBtns.forEach(function(btn){
            btn.style.color = "rgba(0,0,0,1)";
        });
        //
        Artery.get('atyuploadbtnsm').hide()
        Artery.get('atyUploadAejkv2hmd').show()

    }
}
/**
 * 值改变时脚本(zskSearchIds)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  newValue 控件改变后的新值
 * @param  oldValue 控件改变前的旧值
 */
function zskSearchIdsOnChangeClient (rc, newValue, oldValue){
    // newValue 是 "1,2,3" 这种格式
    if (typeof newValue === 'string' && newValue.trim() !== '') {
        hmdRagZsk = newValue.split(',').map(function(item) {
            return parseInt(item, 10);
        }).filter(function(item) {
            return !isNaN(item);
        });
    } else {
        hmdRagZsk = [];
    }
}



/**
 * 值改变时脚本(zskSearchIds)
 *
 * @param  rc 系统提供的AJAX调用对象
 * @param  newValue 控件改变后的新值
 * @param  oldValue 控件改变前的旧值
 */
function zskSearchIdscomOnChangeClient (rc, newValue, oldValue){
    // newValue 是 "1,2,3" 这种格式
    if (typeof newValue === 'string' && newValue.trim() !== '') {
        hmdRagZsk = newValue.split(',').map(function(item) {
            return parseInt(item, 10);
        }).filter(function(item) {
            return !isNaN(item);
        });
    } else {
        hmdRagZsk = [];
    }
}



/**
 * 点击时触发客户端脚本(atyIconKrivz1)
 *
 * @param rc 系统提供的AJAX调用对象
 */
function atyIconKrivz2OnClickClient(rc) {
    window.event.stopPropagation();
    
    // 切换样式 collapseicon-zksq  切换为 expandicon-zksq
    var $icon = $('#atyIconKrivz1');
    if ($icon.hasClass('collapseicon-zksq')) {
        $icon.removeClass('collapseicon-zksq').addClass('expandicon-zksq');
    } else {
        $icon.removeClass('expandicon-zksq').addClass('collapseicon-zksq');
    }
    
    
    
    // 切换显示隐藏
    var $element = $('#atyCheckBoxzskcom');
    if ($element.length) {
        $element.toggle();
    }
}