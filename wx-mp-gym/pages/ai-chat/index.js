var app = getApp();
var request = require('../../utils/request.js');

var streamingReq = null; // 用于中途取消

function arrayBufferToString(buffer) {
  var bytes = new Uint8Array(buffer);
  var str = "";
  for (var i = 0; i < bytes.length; i++) {
    str += String.fromCharCode(bytes[i]);
  }
  return decodeURIComponent(escape(str));
}

Page({
  data: {
    inputText: '',
    scrollTarget: '',
    messages: [],
    headerPadding: 44,
    showDateDivider: false,
    dateDividerText: '',
    sessionId: '',
    isLoggedIn: false,
    isStreaming: false  // 流式输出状态
  },

  onLoad() {
    var _this = this;
    wx.getSystemInfo({ success: function(res) { _this.setData({ headerPadding: res.statusBarHeight + 10 || 44 }); } });
    this.setData({
      sessionId: 'session_' + Date.now() + '_' + Math.random(),
      messages: [{
        role: 'ai',
        content: '你好！我是桔刻健身的AI助手，可以帮你：\n\n· 制定训练计划\n· 解答健身问题\n· 体测评估分析\n· 饮食建议',
        time: Date.now()
      }]
    });
    this.checkLogin();
  },

  onShow() { this.checkLogin(); },

  onUnload() {
    // 页面退出时取消流式请求
    if (streamingReq) {
      streamingReq.abort();
      streamingReq = null;
    }
  },

  checkLogin() { this.setData({ isLoggedIn: app.isLoggedIn() }); },

  onInput(e) { this.setData({ inputText: e.detail.value }); },

  onQuickAsk(e) {
    if (!this.data.isLoggedIn) { app.requireAuth(null); return; }
    var msg = e.currentTarget.dataset.msg;
    this.setData({ inputText: msg });
    this.onSend();
  },

  getDateLabel(ts) {
    var d = new Date(ts);
    var today = new Date(); today.setHours(0, 0, 0, 0);
    var msgDate = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    var diffDays = Math.floor((today - msgDate) / 86400000);
    if (diffDays === 0) return '今天';
    if (diffDays === 1) return '昨天';
    return '更早';
  },

  onSend() {
    if (!this.data.isLoggedIn) { app.requireAuth(null); return; }
    if (this.data.isStreaming) return; // 防止重复发送

    var text = this.data.inputText;
    if (!text.trim()) return;

    var msgs = this.data.messages;
    var now = Date.now();
    msgs.push({ role: 'user', content: text, time: now });
    this.setData({ inputText: '', messages: msgs, scrollTarget: 'bottom', showDateDivider: false });

    // 添加"正在输入..."占位气泡
    msgs.push({ role: 'ai', content: '', time: Date.now(), isStreaming: true });
    this.setData({ messages: msgs, isStreaming: true, scrollTarget: 'bottom' });

    var _this = this;
    var baseUrl = "http://192.168.10.6:8080";
    var token = "";
    try { token = wx.getStorageSync("token") || ""; } catch (e) {}

    streamingReq = wx.request({
      url: baseUrl + '/api/ai/chat/stream',
      data: { sessionId: _this.data.sessionId, message: text },
      header: { "Authorization": token ? "Bearer " + token : "" },
      enableChunked: true,
      success: function(res) {
        // 流式完成后，确保收起状态
        _this.finishStreaming();
      },
      fail: function(err) {
        if (err.errMsg && err.errMsg.indexOf("abort") >= 0) return; // 主动取消不提示
        _this.replaceLastAiMessage("网络连接失败，请检查服务器是否启动。");
        _this.finishStreaming();
      }
    });

    streamingReq.onChunkReceived(function(res) {
      var raw = arrayBufferToString(res.data);
      // SSE 格式：data: {...}\n\n，可能有多个事件粘在一起
      var parts = raw.split("\n\n");
      for (var p = 0; p < parts.length; p++) {
        var part = parts[p].trim();
        if (!part) continue;
        // 去掉 "data: " 前缀
        var jsonStr = part;
        if (jsonStr.indexOf("data: ") === 0) {
          jsonStr = jsonStr.substring(6);
        }
        // 去掉 "event: message\n" 等元信息，只保留 data 行
        var dataLine = "";
        var lines = jsonStr.split("\n");
        for (var li = 0; li < lines.length; li++) {
          if (lines[li].indexOf("data: ") === 0) {
            dataLine = lines[li].substring(6);
          } else if (lines[li].indexOf("{") === 0) {
            dataLine = lines[li];
          }
        }
        if (!dataLine) continue;
        try {
          var evt = JSON.parse(dataLine);
          if (evt.type === "token") {
            _this.updateLastAiMessage(evt.full || evt.content);
          } else if (evt.type === "complete") {
            _this.updateLastAiMessage(evt.full);
            _this.finishStreaming();
          } else if (evt.type === "error") {
            _this.replaceLastAiMessage(evt.content || "服务异常，请稍后重试。");
            _this.finishStreaming();
          }
        } catch (e) {
          // 可能是未完整的 JSON，忽略
        }
      }
    });
  },

  // 更新最后一条 AI 消息内容（流式追加）
  updateLastAiMessage: function(text) {
    var msgs = this.data.messages;
    for (var i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'ai') {
        msgs[i].content = text;
        msgs[i].isStreaming = true;
        break;
      }
    }
    this.setData({ messages: msgs, scrollTarget: 'bottom' });
  },

  // 替换最后一条 AI 消息（完成态）
  replaceLastAiMessage: function(text) {
    var msgs = this.data.messages;
    for (var i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'ai') {
        msgs[i].content = text;
        msgs[i].isStreaming = false;
        break;
      }
    }
    this.setData({ messages: msgs, scrollTarget: 'bottom' });
  },

  // 结束流式输出
  finishStreaming: function() {
    streamingReq = null;
    var msgs = this.data.messages;
    for (var i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'ai') {
        msgs[i].isStreaming = false;
        if (!msgs[i].content) {
          msgs[i].content = '抱歉，暂时无法回答，请稍后再试。';
        }
        break;
      }
    }
    this.setData({ messages: msgs, isStreaming: false, scrollTarget: 'bottom' });
  },

  // 取消流式输出
  onCancelStream: function() {
    if (streamingReq) {
      streamingReq.abort();
      streamingReq = null;
    }
    this.finishStreaming();
  },
});

