var app = getApp();
Page({
  data: { today: '', schedules: [] },
  onShow() {
    var d = new Date();
    this.setData({ today: d.getFullYear() + '.' + (d.getMonth() + 1) + '.' + d.getDate() });
    this.loadSchedules();
  },
  loadSchedules() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/trainers/' + (app.globalData.userInfo?.trainerId || 0) + '/today-schedule', {}, function(r) {
      if (r) _this.setData({ schedules: r.list || r || [] });
    });
  },
  goDetail(e) {
    var id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/coach-appointments/index?highlight=' + id });
  }
});
