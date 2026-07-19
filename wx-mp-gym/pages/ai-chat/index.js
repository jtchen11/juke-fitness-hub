var app = getApp();
Page({
  data: { inputText: '', scrollTarget: '', messages: [], headerPadding: 44, showDateDivider: false, dateDividerText: '' },
  onLoad() {
    var _this = this;
    wx.getSystemInfo({ success: function(res) { _this.setData({ headerPadding: res.statusBarHeight + 10 || 44 }); } });
    this.setData({ messages: [{ role: 'ai', content: '你好！我是桔刻健身的AI助手，可以帮你：\n\n• 制定训练计划\n• 解答健身问题\n• 体测评估分析\n• 饮食建议', time: Date.now() }] });
  },
  onInput(e) { this.setData({ inputText: e.detail.value }); },
  onQuickAsk(e) {
    var msg = e.currentTarget.dataset.msg;
    this.setData({ inputText: msg });
    this.onSend();
  },
  getDateLabel(ts) {
    var d = new Date(ts);
    var today = new Date(); today.setHours(0,0,0,0);
    var msgDate = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    var diffDays = Math.floor((today - msgDate) / 86400000);
    if (diffDays === 0) return '今天';
    if (diffDays === 1) return '昨天';
    return '更早';
  },
  onSend() {
    var text = this.data.inputText;
    if (!text.trim()) return;
    var msgs = this.data.messages;
    var now = Date.now();
    msgs.push({ role: 'user', content: text, time: now });
    this.setData({ inputText: '', messages: msgs, scrollTarget: 'bottom', showDateDivider: false });
    var _this = this;
    wx.request({
      url: (app.globalData.apiBaseUrl || 'http://localhost:8080') + '/api/ai/chat',
      method: 'POST',
      header: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (app.globalData.token || '') },
      data: { message: text },
      success: function(res) {
        var reply = (res.data && res.data.reply) || (res.data && res.data.content) || '';
        if (!reply && res.data && res.data.data) reply = res.data.data.reply || res.data.data.content || '';
        if (!reply) reply = '抱歉，我暂时无法回答这个问题，请稍后再试。';
        var label = _this.getDateLabel(now);
        _this.setData({ showDateDivider: true, dateDividerText: label });
        msgs.push({ role: 'ai', content: reply, time: Date.now() });
        _this.setData({ messages: msgs, scrollTarget: 'bottom' });
      },
      fail: function() {
        msgs.push({ role: 'ai', content: '网络连接失败，请检查服务器是否启动。' });
        _this.setData({ messages: msgs, scrollTarget: 'bottom' });
      }
    });
  }
});