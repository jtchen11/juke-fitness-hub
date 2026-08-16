var app = getApp();
var request = require('../../utils/request.js');

var streamingReq = null;

// 跨块 UTF-8 解码器（stream:true 保持多字节字符连续性）
var chunkDecoder = new TextDecoder("utf-8");
// SSE 跨块缓冲（累积不完整的分割事件）
var sseBuffer = "";
var hasReceivedChunk = false;
var _completeHandled = false;

function arrayBufferToString(buffer) {
  return chunkDecoder.decode(buffer, { stream: true });
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
    var sid = wx.getStorageSync("chat_session_id") || "";
    console.log("[ai-chat] onLoad: existing sid=" + sid);
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
    _this.loadHistory(sid, welcomeMsg);
  },

  onShow() {
    this.checkLogin();
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
    if (!this.data.isLoggedIn) { wx.showToast({ title: "请先登录再使用AI助手", icon: "none" }); setTimeout(function() { wx.navigateTo({ url: "/pages/login/login" }); }, 10000); return; }
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

    var reqId = Date.now() + "_" + Math.random();
    var req = wx.request({
      url: baseUrl + "/api/ai/chat/stream",
      data: { sessionId: _this.data.sessionId, message: text },
      header: { "Authorization": token ? "Bearer " + token : "" },
      enableChunked: true,
      timeout: 120000,
      success: function(res) {
        if (_completeHandled) { _completeHandled = false; return; }
        if (streamingReq === req && _this.data.isStreaming) {
          _this.finishStreaming();
        }
      },
      fail: function(err) {
        if (err.errMsg && err.errMsg.indexOf("abort") >= 0) return;
        if (streamingReq !== req) return;
        hasReceivedChunk = false;
        _this.replaceLastAiMessage("网络连接失败，请检查服务器是否启动。");
        _this.finishStreaming();
      }
    });
    streamingReq = req;

    req.onChunkReceived(function(res) {
      hasReceivedChunk = true;
      try {
        // 流式解码，保持多字节字符跨块连续性
        var decoded = chunkDecoder.decode(res.data, { stream: true });
        sseBuffer += decoded;
        // 按 SSE 双换行符切割完整事件
        var parts = sseBuffer.split("\n\n");
        // 最后一个片段可能不完整，留在缓冲区
        sseBuffer = parts.pop();
        for (var p = 0; p < parts.length; p++) {
          var part = parts[p].trim();
          if (!part) continue;
          var jsonStr = part;
          if (jsonStr.indexOf("data: ") === 0) {
            jsonStr = jsonStr.substring(6);
          }
          var dataLine = "";
          var slines = jsonStr.split("\n");
          for (var li = 0; li < slines.length; li++) {
            if (slines[li].indexOf("data:") === 0) {
              dataLine = slines[li].substring(5).trim();
            } else if (slines[li].indexOf("{") === 0) {
              dataLine = slines[li];
            }
          }
          if (!dataLine) continue;
          try {
            var evt = JSON.parse(dataLine);
            if (evt.type === "delta" || evt.type === "token") {
              _this.updateLastAiMessage(evt.content || evt.full || "");
            } else if (evt.type === "tool_result") {
              var toolText = evt.content || "";
              // 检测 **PAYMENT** 标记，解析为支付按钮
              if (toolText.indexOf("**PAYMENT_GROUP**") >= 0) {
                var pparts = toolText.split("**PAYMENT_GROUP**");
                var displayText = pparts[0].trim();
                var optionsText = pparts[1] || "";
                var payOptions = [];
                if (optionsText.indexOf("confirm") >= 0 || optionsText.indexOf("确认") >= 0) {
                  payOptions.push({ label: "确认支付", sub: "", payValue: "confirm" });
                }
                console.log("[payment] group tool_result parsed", payOptions.length, "options");
                if (payOptions.length > 0) {
                  var msgs = _this.data.messages;
                  for (var mi = msgs.length - 1; mi >= 0; mi--) {
                    if (msgs[mi].role === "ai") {
                      msgs[mi].content = displayText;
                      msgs[mi].paymentOptions = payOptions;
                      msgs[mi].isStreaming = false;
                      break;
                    }
                  }
                  _this.setData({ messages: msgs, isStreaming: false, scrollTarget: "bottom" });
                  continue;
                }
              }
              if (toolText.indexOf("**PAYMENT**") >= 0) {
                var pparts = toolText.split("**PAYMENT**");
                var displayText = pparts[0].trim();
                var optionsText = pparts[1] || "";
                var payOptions = [];
                var optLines = optionsText.split("\n");
                var lastMain = -1;
                for (var oi = 0; oi < optLines.length; oi++) {
                  var optLine = optLines[oi].trim();
                  if (!optLine) continue;
                  // 子行（待激活课程包的展开项）：以 "-" 或 "·" 开头
                  if (optLine.indexOf("-") === 0 || optLine.indexOf("\u00b7") === 0) {
                    if (lastMain < 0) continue;
                    var restSub = optLine.substring(1).trim();
                    var childPay = "";
                    var pm2 = restSub.match(/\[pkg=(\d+)\]/);
                    if (pm2) {
                      childPay = "pkg=" + pm2[1];
                      restSub = restSub.replace(/\[pkg=\d+\]/, "").trim();
                    }
                    var labelSub = restSub;
                    var subSub = "";
                    var cpp1 = restSub.indexOf("(");
                    var cpp2 = restSub.indexOf("\uff08");
                    var cppos = (cpp1 >= 0 && (cpp2 < 0 || cpp1 < cpp2)) ? cpp1 : cpp2;
                    if (cppos >= 0) {
                      labelSub = restSub.substring(0, cppos).trim();
                      var csubEnd = restSub.lastIndexOf(")") >= 0 ? restSub.lastIndexOf(")") : restSub.lastIndexOf("\uff09");
                      if (csubEnd > cppos) { subSub = restSub.substring(cppos + 1, csubEnd).trim(); }
                    }
                    if (!payOptions[lastMain].children) payOptions[lastMain].children = [];
                    payOptions[lastMain].children.push({ label: labelSub, sub: subSub, payValue: childPay });
                    continue;
                  }
                  var dotPos = optLine.indexOf(".");
                  if (dotPos > 0 && dotPos < 3) {
                    var val = optLine.substring(0, dotPos).trim();
                    var rest = optLine.substring(dotPos + 1).trim();
                    var label = rest;
                    var sub = "";
                    var pp1 = rest.indexOf("(");
                    var pp2 = rest.indexOf("\uff08");
                    var ppos = (pp1 >= 0 && (pp2 < 0 || pp1 < pp2)) ? pp1 : pp2;
                    if (ppos >= 0) {
                      label = rest.substring(0, ppos).trim();
                      var subEnd = rest.lastIndexOf(")") >= 0 ? rest.lastIndexOf(")") : rest.lastIndexOf("\uff09");
                      if (subEnd > ppos) { sub = rest.substring(ppos + 1, subEnd).trim(); }
                    }
                    payOptions.push({ label: label, sub: sub, payValue: val, children: [] });
                    lastMain = payOptions.length - 1;
                  }
                }
                console.log("[payment] tool_result parsed", payOptions.length, "options");
                if (payOptions.length > 0) {
                  var msgs2 = _this.data.messages;
                  for (var mi2 = msgs2.length - 1; mi2 >= 0; mi2--) {
                    if (msgs2[mi2].role === "ai") {
                      msgs2[mi2].content = displayText;
                      msgs2[mi2].paymentOptions = payOptions;
                      msgs2[mi2].isStreaming = false;
                      break;
                    }
                  }
                  _this.setData({ messages: msgs2, isStreaming: false, scrollTarget: "bottom" });
                  continue;
                }
              }
              var msgs = _this.data.messages;
              // 填充最后一条 AI 占位消息，不新增 system 消息
              var found = false;
              for (var mi = msgs.length - 1; mi >= 0; mi--) {
                if (msgs[mi].role === "ai") {
                  msgs[mi].content = toolText;
                  msgs[mi].isStreaming = false;
                  found = true;
                  break;
                }
              }
              if (!found) {
                msgs.push({ role: "ai", content: toolText, time: Date.now(), isStreaming: false });
              }
              _this.setData({ messages: msgs, isStreaming: false, scrollTarget: "bottom" });
            } else if (evt.type === "end") {
              _this.finishStreaming();
            } else if (evt.type === "complete") {
              var fullText = evt.full || "";
              if (fullText.indexOf("**PAYMENT_GROUP**") >= 0) {
                console.log("[payment] fullText contains **PAYMENT_GROUP**");
                var pparts = fullText.split("**PAYMENT_GROUP**");
                var displayText = pparts[0].trim();
                var optionsText = pparts[1] || "";
                var payOptions = [];
                if (optionsText.indexOf("confirm") >= 0 || optionsText.indexOf("确认") >= 0) {
                  payOptions.push({ label: "确认支付", sub: "", payValue: "confirm" });
                }
                console.log("[payment] group parsed", payOptions.length, "options");
                if (payOptions.length > 0) {
                  _completeHandled = true;
                  var msgs = _this.data.messages;
                  for (var mi = msgs.length - 1; mi >= 0; mi--) {
                    if (msgs[mi].role === "ai") {
                      msgs[mi].content = displayText;
                      msgs[mi].paymentOptions = payOptions;
                      msgs[mi].isStreaming = false;
                      break;
                    }
                  }
                  _this.setData({ messages: msgs, isStreaming: false, scrollTarget: "bottom" });
                  continue;
                }
              }
              if (fullText.indexOf("**PAYMENT**") >= 0) {
                console.log("[payment] fullText contains **PAYMENT**");
                var pparts = fullText.split("**PAYMENT**");
                var displayText = pparts[0].trim();
                var optionsText = pparts[1] || "";
                var payOptions = [];
                var optLines = optionsText.split("\n");
                var lastMain = -1;
                for (var oi = 0; oi < optLines.length; oi++) {
                  var optLine = optLines[oi].trim();
                  if (!optLine) continue;
                  // 子行（待激活课程包的展开项）：以 "-" 或 "·" 开头
                  if (optLine.indexOf("-") === 0 || optLine.indexOf("\u00b7") === 0) {
                    if (lastMain < 0) continue;
                    var restSub = optLine.substring(1).trim();
                    var childPay = "";
                    var pm2 = restSub.match(/\[pkg=(\d+)\]/);
                    if (pm2) {
                      childPay = "pkg=" + pm2[1];
                      restSub = restSub.replace(/\[pkg=\d+\]/, "").trim();
                    }
                    var labelSub = restSub;
                    var subSub = "";
                    var cpp1 = restSub.indexOf("(");
                    var cpp2 = restSub.indexOf("\uff08");
                    var cppos = (cpp1 >= 0 && (cpp2 < 0 || cpp1 < cpp2)) ? cpp1 : cpp2;
                    if (cppos >= 0) {
                      labelSub = restSub.substring(0, cppos).trim();
                      var csubEnd = restSub.lastIndexOf(")") >= 0 ? restSub.lastIndexOf(")") : restSub.lastIndexOf("\uff09");
                      if (csubEnd > cppos) { subSub = restSub.substring(cppos + 1, csubEnd).trim(); }
                    }
                    if (!payOptions[lastMain].children) payOptions[lastMain].children = [];
                    payOptions[lastMain].children.push({ label: labelSub, sub: subSub, payValue: childPay });
                    continue;
                  }
                  var dotPos = optLine.indexOf(".");
                  if (dotPos > 0 && dotPos < 3) {
                    var val = optLine.substring(0, dotPos).trim();
                    var rest = optLine.substring(dotPos + 1).trim();
                    var label = rest;
                    var sub = "";
                    var pp1 = rest.indexOf("(");
                    var pp2 = rest.indexOf("\uff08");
                    var ppos = (pp1 >= 0 && (pp2 < 0 || pp1 < pp2)) ? pp1 : pp2;
                    if (ppos >= 0) {
                      label = rest.substring(0, ppos).trim();
                      var subEnd = rest.lastIndexOf(")") >= 0 ? rest.lastIndexOf(")") : rest.lastIndexOf("\uff09");
                      if (subEnd > ppos) { sub = rest.substring(ppos + 1, subEnd).trim(); }
                    }
                    payOptions.push({ label: label, sub: sub, payValue: val, children: [] });
                    lastMain = payOptions.length - 1;
                  }
                }
                console.log("[payment] parsed", payOptions.length, "options");
                if (payOptions.length > 0) {
                  _completeHandled = true;
                  var msgs = _this.data.messages;
                  for (var mi = msgs.length - 1; mi >= 0; mi--) {
                    if (msgs[mi].role === "ai") {
                      msgs[mi].content = displayText;
                      msgs[mi].paymentOptions = payOptions;
                      msgs[mi].isStreaming = false;
                      break;
                    }
                  }
                  _this.setData({ messages: msgs, isStreaming: false, scrollTarget: "bottom" });
                  continue;
                }
              }
              _this.updateLastAiMessage(evt.full);
              _this.finishStreaming();
            } else if (evt.type === "error") {
              _this.replaceLastAiMessage(evt.content || "服务异常，请稍后重试。");
              _this.finishStreaming();
            }
          } catch (e) { console.warn("[ai-chat] parse event error:", e, "dataLine:", dataLine); }
        }
      } catch (e) { console.warn("[ai-chat] decode chunk error:", e); }
    });
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

  updateLastAiMessage: function(text) {
    var msgs = this.data.messages;
    for (var i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === "ai") {
        if (!msgs[i].content) {
          msgs[i].content = text;
        } else {
          msgs[i].content += text;
        }
        msgs[i].isStreaming = true;
        break;
      }
    }
    this.setData({ messages: msgs, scrollTarget: "bottom" });
  },

  // 解析 **PAYMENT** / **PAYMENT_GROUP** 标记文本，渲染为可点击的支付按钮
  // 逻辑与 tool_result / complete 分支中的解析代码保持一致（含 children 子项处理）
  parsePaymentOptions: function(fullText) {
    if (!fullText) return false;
    if (fullText.indexOf("**PAYMENT_GROUP**") >= 0) {
      var pparts = fullText.split("**PAYMENT_GROUP**");
      var displayText = pparts[0].trim();
      var optionsText = pparts[1] || "";
      var payOptions = [];
      if (optionsText.indexOf("confirm") >= 0 || optionsText.indexOf("确认") >= 0) {
        payOptions.push({ label: "确认支付", sub: "", payValue: "confirm" });
      }
      console.log("[payment] parsePaymentOptions group parsed", payOptions.length, "options");
      if (payOptions.length > 0) {
        var msgs = this.data.messages;
        for (var mi = msgs.length - 1; mi >= 0; mi--) {
          if (msgs[mi].role === "ai") {
            msgs[mi].content = displayText;
            msgs[mi].paymentOptions = payOptions;
            msgs[mi].isStreaming = false;
            break;
          }
        }
        this.setData({ messages: msgs, scrollTarget: "bottom" });
        return true;
      }
      return false;
    }
    if (fullText.indexOf("**PAYMENT**") >= 0) {
      var pparts = fullText.split("**PAYMENT**");
      var displayText = pparts[0].trim();
      var optionsText = pparts[1] || "";
      var payOptions = [];
      var optLines = optionsText.split("\n");
      var lastMain = -1;
      for (var oi = 0; oi < optLines.length; oi++) {
        var optLine = optLines[oi].trim();
        if (!optLine) continue;
        // 子行（待激活课程包的展开项）：以 "-" 或 "·" 开头
        if (optLine.indexOf("-") === 0 || optLine.indexOf("\u00b7") === 0) {
          if (lastMain < 0) continue;
          var restSub = optLine.substring(1).trim();
          var childPay = "";
          var pm2 = restSub.match(/\[pkg=(\d+)\]/);
          if (pm2) {
            childPay = "pkg=" + pm2[1];
            restSub = restSub.replace(/\[pkg=\d+\]/, "").trim();
          }
          var labelSub = restSub;
          var subSub = "";
          var cpp1 = restSub.indexOf("(");
          var cpp2 = restSub.indexOf("\uff08");
          var cppos = (cpp1 >= 0 && (cpp2 < 0 || cpp1 < cpp2)) ? cpp1 : cpp2;
          if (cppos >= 0) {
            labelSub = restSub.substring(0, cppos).trim();
            var csubEnd = restSub.lastIndexOf(")") >= 0 ? restSub.lastIndexOf(")") : restSub.lastIndexOf("\uff09");
            if (csubEnd > cppos) { subSub = restSub.substring(cppos + 1, csubEnd).trim(); }
          }
          if (!payOptions[lastMain].children) payOptions[lastMain].children = [];
          payOptions[lastMain].children.push({ label: labelSub, sub: subSub, payValue: childPay });
          continue;
        }
        var dotPos = optLine.indexOf(".");
        if (dotPos > 0 && dotPos < 3) {
          var val = optLine.substring(0, dotPos).trim();
          var rest = optLine.substring(dotPos + 1).trim();
          var label = rest;
          var sub = "";
          var pp1 = rest.indexOf("(");
          var pp2 = rest.indexOf("\uff08");
          var ppos = (pp1 >= 0 && (pp2 < 0 || pp1 < pp2)) ? pp1 : pp2;
          if (ppos >= 0) {
            label = rest.substring(0, ppos).trim();
            var subEnd = rest.lastIndexOf(")") >= 0 ? rest.lastIndexOf(")") : rest.lastIndexOf("\uff09");
            if (subEnd > ppos) { sub = rest.substring(ppos + 1, subEnd).trim(); }
          }
          payOptions.push({ label: label, sub: sub, payValue: val, children: [] });
          lastMain = payOptions.length - 1;
        }
      }
      console.log("[payment] parsePaymentOptions parsed", payOptions.length, "options");
      if (payOptions.length > 0) {
        var msgs2 = this.data.messages;
        for (var mi2 = msgs2.length - 1; mi2 >= 0; mi2--) {
          if (msgs2[mi2].role === "ai") {
            msgs2[mi2].content = displayText;
            msgs2[mi2].paymentOptions = payOptions;
            msgs2[mi2].isStreaming = false;
            break;
          }
        }
        this.setData({ messages: msgs2, scrollTarget: "bottom" });
        return true;
      }
    }
    return false;
  },
  finishStreaming: function() {
    streamingReq = null;
    var msgs = this.data.messages;
    var lastMsg = msgs[msgs.length - 1];
    // 流结束时：若最后一条 AI 消息包含 **PAYMENT** / **PAYMENT_GROUP** 标记
    // （delta 逐字推送场景，标记可能分散在多个 chunk 中），
    // 调用 parsePaymentOptions 解析为可点击的支付按钮，随后再统一 setData
    if (lastMsg && lastMsg.role === "ai" && lastMsg.content &&
        (lastMsg.content.indexOf("**PAYMENT**") >= 0 || lastMsg.content.indexOf("**PAYMENT_GROUP**") >= 0)) {
      this.parsePaymentOptions(lastMsg.content);
    }
    for (var i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === "ai") {
        msgs[i].isStreaming = false;
        // 只有完全没有收到任何数据时才显示"抱歉"
        if (!msgs[i].content && !hasReceivedChunk) {
          msgs[i].content = "抱歉，暂时无法回答，请稍后再试。";
        }
        break;
      }
    }
    sseBuffer = "";
    hasReceivedChunk = false;
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

  onPaymentSelect: function(e) {
    var pay = e.currentTarget.dataset.pay;
    var msgIdx = e.currentTarget.dataset.msg;
    var optIdx = e.currentTarget.dataset.opt;
    var isChild = e.currentTarget.dataset.child;
    if (!pay) return;
    var msgs = this.data.messages;
    if (!isChild) {
      var opt = null;
      if (msgIdx !== undefined && msgIdx !== "" && msgs[msgIdx] && msgs[msgIdx].paymentOptions && optIdx !== undefined && optIdx !== "") {
        opt = msgs[msgIdx].paymentOptions[optIdx];
      }
      if (opt && opt.children && opt.children.length > 0) {
        // 展开/收起“待激活课程包”
        msgs[msgIdx].paymentExpanded = (msgs[msgIdx].paymentExpanded === optIdx) ? -1 : optIdx;
        this.setData({ messages: msgs });
        return;
      }
    }
    this.setData({ inputText: pay });
    this.onSend();
  },

  onCancelStream: function() {
    if (streamingReq) {
      streamingReq.abort();
      streamingReq = null;
    }
    this.finishStreaming();
  }
});