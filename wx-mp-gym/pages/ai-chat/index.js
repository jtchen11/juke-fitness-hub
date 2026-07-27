var app = getApp();
var request = require('../../utils/request.js');

var streamingReq = null;

function arrayBufferToString(buffer) {
  return new TextDecoder("utf-8").decode(buffer);
}

Page({
  data: {
    inputText: "",
    scrollTarget: "",
    messages: [],
    headerPadding: 44,
    showDateDivider: false,
    dateDividerText: "",
    sessionId: "",
    isLoggedIn: false,
    isStreaming: false
  },

  onLoad() {
    var _this = this;
    // 会话 ID 持久化：先尝试从 Storage 读取
    var sid = wx.getStorageSync("chat_session_id") || "";
    console.log("[ai-chat] onLoad: existing sid=" + sid);
    // 如果已登录，用 memberId 构建固定 sessionId
    var memberId = "";
    try {
      var storedInfo = wx.getStorageSync("userInfo") || {};
      console.log("[ai-chat] storedUserInfo=", JSON.stringify(storedInfo));
      if (storedInfo) {
        memberId = storedInfo.id || storedInfo.memberId || "";
      }
    } catch (e) {
      console.warn("[ai-chat] 获取 userInfo 失败", e);
    }
    if (memberId) {
      sid = "chat_" + memberId;
    } else {
      sid = "session_" + Date.now() + "_" + Math.random();
    }
    wx.setStorageSync("chat_session_id", sid);
    console.log("[ai-chat] onLoad: final sid=" + sid + ", memberId=" + memberId);
    var welcomeMsg = {
      role: "ai",
      content: "你好！我是桔刻健身的AI助手，可以帮你：\n\n· 制定训练计划\n· 解答健身问题\n· 体测评估分析\n· 饮食建议",
      time: Date.now()
    };
    this.setData({
      sessionId: sid,
      messages: [welcomeMsg]
    });
    this.checkLogin();
    // 异步加载历史记录
    _this.loadHistory(sid, welcomeMsg);
  },

  onShow() {
    this.checkLogin();
    // 每次显示页面时重新检查 memberId，确保登录后 sessionId 更新
    var currentSid = this.data.sessionId;
    var memberId = "";
    try {
      var stored = wx.getStorageSync("userInfo") || {};
      memberId = stored.id || stored.memberId || "";
      if (!memberId && app.globalData && app.globalData.userInfo) {
        memberId = app.globalData.userInfo.id || app.globalData.userInfo.memberId || "";
      }
    } catch (e) {}
    if (memberId) {
      var newSid = "chat_" + memberId;
      if (currentSid !== newSid) {
        console.log("[ai-chat] onShow: updating sid from " + currentSid + " to " + newSid);
        this.setData({ sessionId: newSid });
        wx.setStorageSync("chat_session_id", newSid);
      }
    }
  },

  onUnload() {
    if (streamingReq) {
      streamingReq.abort();
      streamingReq = null;
    }
  },

  checkLogin() { this.setData({ isLoggedIn: app.isLoggedIn() }); },

  onInput(e) { this.setData({ inputText: e.detail.value }); },

  onQuickAsk(e) {
    if (!this.data.isLoggedIn) { wx.showToast({ title: "请先登录再使用AI助手", icon: "none" }); setTimeout(function() { wx.navigateTo({ url: "/pages/login/login" }); }, 1000); return; }
    var msg = e.currentTarget.dataset.msg;
    this.setData({ inputText: msg });
    this.onSend();
  },

  getDateLabel(ts) {
    var d = new Date(ts);
    var today = new Date(); today.setHours(0, 0, 0, 0);
    var msgDate = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    var diffDays = Math.floor((today - msgDate) / 86400000);
    if (diffDays === 0) return "今天";
    if (diffDays === 1) return "昨天";
    return "更早";
  },

  onSend() {
    if (!this.data.isLoggedIn) { wx.showToast({ title: "请先登录再使用AI助手", icon: "none" }); setTimeout(function() { wx.navigateTo({ url: "/pages/login/login" }); }, 1000); return; }
    if (this.data.isStreaming) return;

    var text = this.data.inputText;
    if (!text.trim()) return;

    var msgs = this.data.messages;
    var now = Date.now();
    msgs.push({ role: "user", content: text, time: now });
    this.setData({ inputText: "", messages: msgs, scrollTarget: "bottom", showDateDivider: false });

    msgs.push({ role: "ai", content: "", time: Date.now(), isStreaming: true });
    this.setData({ messages: msgs, isStreaming: true, scrollTarget: "bottom" });

    var _this = this;
    var baseUrl = request.getBaseUrl();
    var token = "";
    try { token = wx.getStorageSync("token") || ""; } catch (e) {}

    streamingReq = wx.request({
      url: baseUrl + "/api/ai/chat/stream",
      data: { sessionId: _this.data.sessionId, message: text },
      header: { "Authorization": token ? "Bearer " + token : "" },
      enableChunked: true,
      timeout: 120000,
      success: function(res) {
        if (_this.data.isStreaming) {
          _this.finishStreaming();
        }
      },
      fail: function(err) {
        if (err.errMsg && err.errMsg.indexOf("abort") >= 0) return;
        _this.replaceLastAiMessage("网络连接失败，请检查服务器是否启动。");
        _this.finishStreaming();
      }
    });

    streamingReq.onChunkReceived(function(res) {
      var raw = arrayBufferToString(res.data);
      var parts = raw.split("\n\n");
      for (var p = 0; p < parts.length; p++) {
        var part = parts[p].trim();
        if (!part) continue;
        var jsonStr = part;
        if (jsonStr.indexOf("data: ") === 0) {
          jsonStr = jsonStr.substring(6);
        }
        var dataLine = "";
        var lines = jsonStr.split("\n");
        for (var li = 0; li < lines.length; li++) {
          if (lines[li].indexOf("data:") === 0) {
            var afterData = lines[li].substring(5).trim();
            dataLine = afterData;
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
        } catch (e) {}
      }
    });
  },

  updateLastAiMessage: function(text) {
    var msgs = this.data.messages;
    for (var i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === "ai") {
        msgs[i].content = text;
        msgs[i].isStreaming = true;
        break;
      }
    }
    this.setData({ messages: msgs, scrollTarget: "bottom" });
  },

  replaceLastAiMessage: function(text) {
    var msgs = this.data.messages;
    for (var i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === "ai") {
        msgs[i].content = text;
        msgs[i].isStreaming = false;
        break;
      }
    }
    this.setData({ messages: msgs, scrollTarget: "bottom" });
  },

  finishStreaming: function() {
    streamingReq = null;
    var msgs = this.data.messages;
    for (var i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === "ai") {
        msgs[i].isStreaming = false;
        if (!msgs[i].content) {
          msgs[i].content = "抱歉，暂时无法回答，请稍后再试。";
        }
        break;
      }
    }
    this.setData({ messages: msgs, isStreaming: false, scrollTarget: "bottom" });
  },

  loadHistory: function(sid, welcomeMsg) {
    var _this = this;
    console.log("[ai-chat] loadHistory: sid=" + sid);
    request.get("/api/ai/chat/history", { sessionId: sid }, function(res) {
      console.log("[ai-chat] loadHistory response:", JSON.stringify(res));
      if (res && res.success && res.history && res.history.length > 0) {
        var historyMsgs = [];
        for (var i = 0; i < res.history.length; i++) {
          var h = res.history[i];
          historyMsgs.push({
            role: (h.role === "user") ? "user" : "ai",
            content: h.content || "",
            time: Date.now()
          });
        }
        // 历史记录追加到欢迎语后面
        var allMsgs = [welcomeMsg].concat(historyMsgs);
        _this.setData({ messages: allMsgs, scrollTarget: "bottom" });
        console.log("[ai-chat] loadHistory: loaded " + res.history.length + " records");
      } else {
        console.log("[ai-chat] loadHistory: no history found");
      }
    }, function(err) {
      console.warn("[ai-chat] loadHistory failed:", err);
    });
  },

  onCancelStream: function() {
    if (streamingReq) {
      streamingReq.abort();
      streamingReq = null;
    }
    this.finishStreaming();
  },
});