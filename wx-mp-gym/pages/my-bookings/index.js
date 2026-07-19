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
      this.setData({ list: res || [] });
    });
  },
  statusText(s) {
    const map = { booked: '???', checked_in: '???', completed: '???', cancelled: '???' };
    return map[s] || s;
  },
  onCheckin(e) {
    const item = e.currentTarget.dataset;
    if (item.type === 'group') {
      request.post('/api/check-in/class/' + item.id, { memberId: app.globalData.userInfo.userId }, (res) => {
        if (res.success) { wx.showToast({ title: '????', icon: 'success' }); this.loadList(); }
        else { wx.showToast({ title: res.message || '????', icon: 'none' }); }
      });
    } else {
      wx.showToast({ title: '????????', icon: 'none' });
    }
  },
  onCancel(e) {
    const item = e.currentTarget.dataset;
    wx.showModal({ title: '????', content: '????????', success: (r) => {
      if (!r.confirm) return;
      request.del('/api/class-bookings/' + item.id, null, (res) => {
        if (res.success) { wx.showToast({ title: '???', icon: 'success' }); this.loadList(); }
        else { wx.showToast({ title: res.message || '????', icon: 'none' }); }
      });
    }});
  }
});