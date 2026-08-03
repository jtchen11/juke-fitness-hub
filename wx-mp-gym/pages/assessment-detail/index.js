var app = getApp();
var request = require('../../utils/request.js');

Page({
  data: {
    record: {},
    report: {},
    score: null,
    bmi: '-',
    compare: { prevDate: '', weight: { text: '', cls: '' }, bodyFat: { text: '', cls: '' }, muscle: { text: '', cls: '' } }
  },

  onLoad(e) { if (e.id) this.loadRecord(e.id); },

  loadRecord(id) {
    var _this = this;
    request.get('/api/fitness-tests/' + id, {}, function(r) {
      if (r) {
        _this.setData({ record: r });
        _this.computeBmi(r);
        _this.loadPrev(r);
      }
    }, function() {});
    request.get('/api/fitness-tests/' + id + '/ai-report', {}, function(r) {
      if (r && r.report) _this.setData({ report: r });
    }, function() {});
    request.get('/api/fitness-tests/' + id + '/score', {}, function(r) {
      if (r && r.totalScore != null) _this.setData({ score: r });
    }, function() {});
  },

  computeBmi(r) {
    var _this = this;
    if (!r.memberId || r.weightKg == null) return;
    request.get('/api/members/' + r.memberId, {}, function(m) {
      if (m && m.height) {
        var hm = Number(m.height) / 100;
        if (hm > 0) _this.setData({ bmi: (Number(r.weightKg) / (hm * hm)).toFixed(1) });
      }
    }, function() {});
  },

  loadPrev(r) {
    var _this = this;
    if (!r.memberId) return;
    request.get('/api/fitness-tests/member/' + r.memberId, {}, function(res) {
      var records = (res && res.records) || [];
      var prev = null;
      for (var i = 0; i < records.length; i++) {
        if (records[i].id === r.id) continue;
        if (!r.testDate || String(records[i].testDate) < String(r.testDate)) { prev = records[i]; break; }
      }
      if (!prev) return;
      var weightD = _this.delta(r.weightKg, prev.weightKg);
      var fatD = _this.delta(r.bodyFatPercent, prev.bodyFatPercent);
      var muscleD = _this.delta(r.muscleMassKg, prev.muscleMassKg);
      _this.setData({
        compare: {
          prevDate: prev.testDate || '',
          weight: _this.deltaObj(weightD, weightD !== null && weightD <= 0),
          bodyFat: _this.deltaObj(fatD, fatD !== null && fatD <= 0),
          muscle: _this.deltaObj(muscleD, muscleD !== null && muscleD >= 0)
        }
      });
    }, function() {});
  },

  delta(cur, prev) {
    if (cur == null || prev == null) return null;
    return Number(cur) - Number(prev);
  },

  deltaObj(d, isGood) {
    if (d === null) return { text: '—', cls: 'flat' };
    var abs = Math.abs(d).toFixed(1);
    return { text: (d > 0 ? '↑ ' : d < 0 ? '↓ ' : '→ ') + abs, cls: d === 0 ? 'flat' : (isGood ? 'good' : 'bad') };
  },

  goBack() { wx.navigateBack(); }
});
