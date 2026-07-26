var app = getApp();
var request = require('../../../utils/request.js');
Page({
  data: { student: {}, stats: { totalClasses: 0, remainingSessions: 0, monthlyCheckins: 0 }, fitnessRecords: [], bookings: [] },
  onLoad(e) { if (e.id) this.loadStudent(e.id); },
  goBack() { wx.navigateBack(); },
  loadStudent(id) {
    var _this = this;
    request.get('/api/members/' + id, {}, function(r) {
      if (r) _this.setData({ student: r });
    });
    request.get('/api/class-bookings/stats/' + id, {}, function(r) {
      if (r) {
        var s = _this.data.stats;
        s.totalClasses = (r.checkedIn || 0) + (r.booked || 0);
        _this.setData({ stats: s });
      }
    });
    request.get('/api/check-in/stats/' + id, {}, function(r) {
      if (r) {
        var s = _this.data.stats;
        s.monthlyCheckins = r.thisMonth || 0;
        _this.setData({ stats: s });
      }
    });
    request.get('/api/members/' + id + '/packages', {}, function(r) {
      if (r && r.length > 0) {
        var totalRemaining = 0;
        r.forEach(function(p) { totalRemaining += (p.remainingSessions || 0); });
        var s = _this.data.stats;
        s.remainingSessions = totalRemaining;
        _this.setData({ stats: s });
      }
    });
    request.get('/api/fitness-tests/member/' + id, {}, function(r) {
      if (r && r.records) _this.setData({ fitnessRecords: r.records.slice(0, 5) });
    });
    request.get('/api/member/bookings', { memberId: id }, function(r) {
      var list = r.list || r || [];
      list.forEach(function(b) {
        b.statusText = _this.statusMap(b.status);
        b.displayDate = (b.bookingTime || b.appointmentTime || '').replace('T', ' ').substring(0, 16);
      });
      _this.setData({ bookings: list.slice(0, 5) });
    });
  },
  statusMap(s) {
    var m = { booked: '待上课', checked_in: '已签到', cancelled: '已取消', scheduled: '待上课', completed: '已完成', ongoing: '进行中' };
    return m[s] || s;
  }
});
