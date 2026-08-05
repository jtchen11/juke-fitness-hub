var app = getApp();
var request = require('../../utils/request.js');

Page({
  data: { loading: false, messages: [], memberId: 0, unreadCount: 0 },

  onShow() {
    var u = app.globalData.userInfo || wx.getStorageSync('userInfo') || {};
    var memberId = u.memberId || u.id;
    if (!memberId) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    this.setData({ memberId: memberId });
    this.load();
  },

  goBack() { wx.navigateBack(); },

  load() {
    var _this = this;
    _this.setData({ loading: true });
    request.get('/api/messages', { memberId: _this.data.memberId, page: 1, size: 50 }, function(r) {
      _this.setData({ loading: false });
      var list = (r && r.list) ? r.list : [];
      var unread = 0;
      for (var i = 0; i < list.length; i++) {
        list[i].title = '系统通知';
        list[i].timeText = _this.formatTime(list[i].createdAt);
        if (!list[i].isRead) unread++;
      }
      _this.setData({ messages: list, unreadCount: unread });
    });
  },

  formatTime(t) {
    if (!t) return '';
    var s = String(t);
    if (s.length >= 16) return s.substr(0, 16).replace('T', ' ');
    return s;
  },

  markRead(e) {
    var id = e.currentTarget.dataset.id;
    var _this = this;
    request.put('/api/messages/' + id + '/read', {}, function() {
      var messages = _this.data.messages;
      for (var i = 0; i < messages.length; i++) {
        if (String(messages[i].id) === String(id) && !messages[i].isRead) {
          messages[i].isRead = true;
          _this.setData({ messages: messages, unreadCount: Math.max(0, _this.data.unreadCount - 1) });
          break;
        }
      }
    });
  },

  markAllRead() {
    var _this = this;
    request.put('/api/messages/read-all?memberId=' + _this.data.memberId, {}, function() {
      var messages = _this.data.messages;
      for (var i = 0; i < messages.length; i++) messages[i].isRead = true;
      _this.setData({ messages: messages, unreadCount: 0 });
      wx.showToast({ title: '已全部标为已读', icon: 'none' });
    });
  }
});
