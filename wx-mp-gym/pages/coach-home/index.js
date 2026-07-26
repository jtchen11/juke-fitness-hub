var app = getApp();
Page({
  data: { today: '', dateDisplay: '', schedules: [], completedCount: 0 },
  onShow() {
    var d = new Date();
    var weekMap = ['日','一','二','三','四','五','六'];
    var dateStr = d.getFullYear() + '年' + (d.getMonth()+1) + '月' + d.getDate() + '日 周' + weekMap[d.getDay()];
    this.setData({ today: d.getFullYear() + '.' + (d.getMonth()+1) + '.' + d.getDate(), dateDisplay: dateStr });
    this.loadSchedules();
  },
  loadSchedules() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/trainers/' + (app.globalData.userInfo?.trainerId || 0) + '/today-schedule', {}, function(r) {
      var list = r.list || r || [];
      var completed = 0;
      list.forEach(function(s) { if (s.status === 'completed') completed++; });
      _this.setData({ schedules: list, completedCount: completed });
    });
  },
  goDetail(e) {
    var id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/coach-appointments/index?highlight=' + id });
  },
  generateCode(e) {
    var id = e.currentTarget.dataset.id;
    var _this = this;
    var request = require('../../utils/request.js');
    wx.showLoading({ title: '正在生成...' });
    request.post('/api/check-in/class/' + id + '/generate-code', {}, function(r) {
      wx.hideLoading();
      if (r && r.success) {
        var st = r.startTime ? r.startTime.substring(11,16) : '开课时间';
        var et = r.endTime ? r.endTime.substring(11,16) : '结束';
        wx.showModal({ title: '签到码', content: '签到码：' + r.code + '\n有效期：' + st + ' - ' + et + '\n请告知会员输入此码签到', showCancel: false });
      } else {
        wx.showToast({ title: (r && r.message) || '生成失败', icon: 'none' });
      }
    });
  }
});