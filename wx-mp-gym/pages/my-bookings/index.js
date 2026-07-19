const app = getApp();
const request = require('../../utils/request.js');
Page({
  data: { filter: 'all', list: [] },
  onShow() { this.loadList(); },
  onFilter(e) {
    const f = e.currentTarget.dataset.filter;
    this.setData({ filter: f });
    this.loadList();
  },
  loadList() {
    if (!app.isLoggedIn()) { this.setData({ list: [] }); return; }
    request.get('/api/member/bookings', { status: this.data.filter }, (res) => {
      var list = res.list || res || [];
      this.setData({ list: list });
    });
  },
  statusText(s) {
    const map = { booked: '已预约', checked_in: '已签到', completed: '已完成', cancelled: '已取消', scheduled: '待上课' };
    return map[s] || s;
  },
  onCheckin(e) {
    var item = e.currentTarget.dataset;
    wx.showToast({ title: '请到店后使用刷脸打卡', icon: 'none' });
  },
  onCancel(e) {
    var item = e.currentTarget.dataset;
    wx.showModal({ title: '确认取消', content: '确定取消该预约吗？', success: function(r) {
      if (!r.confirm) return;
      request.post('/api/class-bookings/' + item.id + '/cancel', {}, function(res) {
        if (res.success) { wx.showToast({ title: '已取消', icon: 'success' }); this.loadList(); }
        else { wx.showToast({ title: res.message || '取消失败', icon: 'none' }); }
      });
    }});
  }
});
