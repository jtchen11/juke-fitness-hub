var app = getApp();
Page({
  data: { nickname: '', stats: { thisMonthSessions: 0, thisMonthBookings: 0, thisMonthCheckins: 0 } },
  onShow() {
    var u = app.globalData.userInfo || {};
    this.setData({ nickname: u.nickname || '教练' });
    this.loadStats();
  },
  loadStats() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/trainers/' + (app.globalData.userInfo?.trainerId || 0) + '/stats', {}, function(r) {
      if (r) _this.setData({ stats: { thisMonthSessions: r.thisMonthSessions || 0, thisMonthBookings: r.thisMonthBookings || 0, thisMonthCheckins: r.thisMonthCheckins || 0 } });
    });
  },
  switchToMember() { app.switchToMemberMode(); },
  goSchedule() { wx.reLaunch({ url: '/pages/coach-home/index' }); },
  goAppointments() { wx.reLaunch({ url: '/pages/coach-appointments/index' }); },
  goStudents() { wx.reLaunch({ url: '/pages/coach-students/index' }); },
  goLeaveRequest() { wx.navigateTo({ url: '/pages/coach-mine/leave-request/index' }); },
  onLogout() {
    wx.showModal({ title: '确认退出', content: '确定退出登录吗？', success: function(r) { if (r.confirm) { app.logout(); wx.reLaunch({ url: '/pages/home/index' }); } } });
  }
});