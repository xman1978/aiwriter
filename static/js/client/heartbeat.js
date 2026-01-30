/*********************************************
 * 心跳检测模块和超时管理
 * 
 * 用于在长时间运行的请求过程中定期发送心跳请求，
 * 并在心跳成功时重置主请求的超时计时器，防止请求超时
 * 
 * 使用单一全局心跳请求，同时管理多个会话的请求对象
 *
 * @author xman
 * @date 2026-01-29
 *********************************************/

// 全局变量：存储所有注册的请求对象获取函数
var _registeredRequestGetters = [];

// 全局变量：心跳检测定时器 ID
var _globalHeartbeatInterval = null;

// 默认心跳配置
var _defaultHeartbeatUrl = "/IntelligentEditor/client/fdqc/heartbeat";
var _defaultHeartbeatInterval = 30000;

/**
 * 创建共用的超时管理函数
 * 用于在 request 函数中设置和管理自定义超时计时器
 * 
 * @param {XMLHttpRequest} request - XMLHttpRequest 对象
 * @param {string} url - 请求URL（用于日志）
 * @param {string} id - 请求ID（用于日志）
 * @param {number} timeoutDuration - 超时时间（毫秒），默认3600000（1小时）
 * @returns {Function} resetCustomTimeout - 重置超时计时器的函数
 */
function createCustomTimeoutManager(request, url, id, timeoutDuration) {
    timeoutDuration = timeoutDuration || 3600000; // 默认1小时
    
    // 记录请求开始时间，用于跟踪超时和心跳检测重置超时计时器
    var requestStartTime = Date.now();
    request._startTime = requestStartTime; // 将开始时间存储到request对象上，供心跳检测使用
    var lastDataReceiveTime = Date.now();
    
    // 自定义超时管理：使用setTimeout来管理超时，可以真正重置
    var customTimeoutTimer = null;
    var CUSTOM_TIMEOUT_DURATION = timeoutDuration;
    
    // 重置自定义超时计时器的函数
    function resetCustomTimeout() {
        // 清除旧的超时计时器
        if (customTimeoutTimer) {
            clearTimeout(customTimeoutTimer);
            customTimeoutTimer = null;
        }
        // 创建新的超时计时器
        customTimeoutTimer = setTimeout(function() {
            var elapsedTime = Date.now() - requestStartTime;
            console.error('[请求超时-自定义] 自定义超时计时器触发，已运行: ' + Math.floor(elapsedTime/1000) + 's, readyState: ' + request.readyState);
            console.error('[请求超时-自定义] 请求URL: ' + url + ', 请求ID: ' + id);
            // 手动触发超时处理
            if (request.readyState !== 4) {
                request.abort(); // 中止请求
                // 尝试重置请求状态变量（兼容不同的变量名）
                try {
                    if (typeof requesting !== 'undefined') {
                        requesting = false;
                    }
                    if (typeof connecting !== 'undefined') {
                        connecting = false;
                    }
                } catch(e) {
                    // 忽略错误
                }
            }
        }, CUSTOM_TIMEOUT_DURATION);
    }
    
    // 初始化超时计时器
    resetCustomTimeout();
    
    // 将重置函数存储到request对象上，供心跳检测使用
    request._resetCustomTimeout = resetCustomTimeout;
    
    // 返回清理函数，用于在请求完成时清理计时器
    return {
        resetCustomTimeout: resetCustomTimeout,
        cleanup: function() {
            if (customTimeoutTimer) {
                clearTimeout(customTimeoutTimer);
                customTimeoutTimer = null;
            }
        }
    };
}

/**
 * 注册请求对象获取函数
 * @param {Function} getRequestFn - 获取当前请求对象的函数，返回 XMLHttpRequest 对象或 null
 */
function registerRequest(getRequestFn) {
    if (getRequestFn && typeof getRequestFn === 'function') {
        // 检查是否已注册
        var index = _registeredRequestGetters.indexOf(getRequestFn);
        if (index === -1) {
            _registeredRequestGetters.push(getRequestFn);
            console.log('[心跳检测] 注册请求对象，当前注册数: ' + _registeredRequestGetters.length);
            
            // 如果还没有启动全局心跳检测，则启动它
            if (!_globalHeartbeatInterval) {
                startGlobalHeartbeat();
            }
        }
    }
}

/**
 * 注销请求对象获取函数
 * @param {Function} getRequestFn - 要注销的请求对象获取函数
 */
function unregisterRequest(getRequestFn) {
    var index = _registeredRequestGetters.indexOf(getRequestFn);
    if (index !== -1) {
        _registeredRequestGetters.splice(index, 1);
        console.log('[心跳检测] 注销请求对象，当前注册数: ' + _registeredRequestGetters.length);
        
        // 如果没有注册的请求对象了，停止全局心跳检测
        if (_registeredRequestGetters.length === 0 && _globalHeartbeatInterval) {
            stopGlobalHeartbeat();
        }
    }
}

/**
 * 启动全局心跳检测（单一心跳请求）
 */
function startGlobalHeartbeat() {
    if (_globalHeartbeatInterval) {
        // 已经启动，不需要重复启动
        return;
    }
    
    console.log('[心跳检测] 启动全局心跳检测');
    _globalHeartbeatInterval = setInterval(() => {
        fetch(_defaultHeartbeatUrl)
            .then(() => {
                // 心跳检测成功，遍历所有注册的请求对象并重置它们的超时计时器
                var activeRequestCount = 0;
                for (var i = 0; i < _registeredRequestGetters.length; i++) {
                    var getRequestFn = _registeredRequestGetters[i];
                    var _request = getRequestFn ? getRequestFn() : null;
                    if (_request && _request.readyState !== 4 && _request.readyState !== 0) {
                        // 主请求存在且未完成（readyState: 1=连接已建立, 2=请求已接收, 3=请求处理中）
                        activeRequestCount++;
                        // 使用自定义超时管理：调用重置函数来真正重置超时计时器
                        if (_request._resetCustomTimeout) {
                            _request._resetCustomTimeout();
                            var currentTime = Date.now();
                            var requestStartTime = _request._startTime || currentTime;
                            var elapsedTime = currentTime - requestStartTime;
                            console.log('[心跳检测] 重置请求对象超时计时器，已运行: ' + Math.floor(elapsedTime/1000) + 's, 新超时时间: 3600s');
                        } else {
                            // 兼容旧代码：如果没有自定义超时管理，使用旧方法
                            var currentTime = Date.now();
                            var requestStartTime = _request._startTime || currentTime;
                            var elapsedTime = currentTime - requestStartTime;
                            var newTimeout = elapsedTime + 3600000;
                            _request.timeout = newTimeout;
                            console.log('[心跳检测] 重置请求对象超时计时器（旧方法），已运行: ' + Math.floor(elapsedTime/1000) + 's, 新超时时间: ' + Math.floor(newTimeout/1000) + 's');
                        }
                    }
                }
                if (activeRequestCount > 0) {
                    console.log('[心跳检测] 心跳成功，重置了 ' + activeRequestCount + ' 个请求对象的超时计时器');
                }
            })
            .catch((error) => {
                // 心跳检测失败不影响主请求
                console.warn('[心跳检测] 心跳请求失败:', error);
            });
    }, _defaultHeartbeatInterval);
}

/**
 * 停止全局心跳检测
 */
function stopGlobalHeartbeat() {
    if (_globalHeartbeatInterval) {
        console.log('[心跳检测] 停止全局心跳检测');
        clearInterval(_globalHeartbeatInterval);
        _globalHeartbeatInterval = null;
    }
}

/**
 * 设置心跳配置（可选）
 * @param {string} heartbeatUrl - 心跳请求的URL
 * @param {number} interval - 心跳检测间隔（毫秒）
 */
function setHeartbeatConfig(heartbeatUrl, interval) {
    if (heartbeatUrl) {
        _defaultHeartbeatUrl = heartbeatUrl;
    }
    if (interval && interval > 0) {
        _defaultHeartbeatInterval = interval;
        // 如果心跳检测正在运行，需要重启以应用新的间隔
        if (_globalHeartbeatInterval) {
            stopGlobalHeartbeat();
            startGlobalHeartbeat();
        }
    }
}
