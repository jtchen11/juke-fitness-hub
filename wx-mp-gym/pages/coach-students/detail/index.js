var app = getApp();
var request = require('../../../utils/request.js');
Page({
  data: { student: {}, fitnessRecords: [], bookings: [] },
  onLoad(e) { if (e.id) this.loadStudent(e.id); },
  loadStudent(id) {
    var _this = this;
    request.get('/api/members/' + id, {}, function(r) {
      if (r) _this.setData({ student: r });
    });
    request.get('/api/fitness-tests/member/' + id, {}, function(r) {
      if (r && r.records) _this.setData({ fitnessRecords: r.records.slice(0, 5) });
    });
    request.get('/api/member/bookings', { memberId: id }, function(r) {
      var list = r.list || r || [];
      _this.setData({ bookings: list.slice(0, 5) });
    });
  }
});