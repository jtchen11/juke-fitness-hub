var app=getApp();var request=require("../../utils/request.js");Page({
  data: { record: {}, report: {}, gradeText: '', gradeClass: '' },
  onLoad(e) { if (e.id) this.loadRecord(e.id); },
  loadRecord(id) {
    var _this = this;
    request.get('/api/fitness-tests/' + id, {}, function(r) {
      if (r) {
        var grades = { excellent: '优秀', good: '良好', pass: '及格', needs_improvement: '需改善' };
        _this.setData({ record: r, gradeText: grades[r.grade] || '', gradeClass: r.grade || '' });
      }
    });
    request.get('/api/fitness-tests/' + id + '/ai-report', {}, function(r) {
      if (r) _this.setData({ report: r });
    });
  }
});