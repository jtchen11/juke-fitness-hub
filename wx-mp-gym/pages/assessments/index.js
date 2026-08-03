var app = getApp();
var request = require('../../utils/request.js');

Page({
  data: {
    memberId: 0,
    height: '',
    latest: { weight: '-', bodyFat: '-', muscle: '-', bmi: '-' },
    deltas: { weight: { text: '', cls: '' }, bodyFat: { text: '', cls: '' }, muscle: { text: '', cls: '' } },
    trend: { dates: [], weights: [], bodyFats: [], muscles: [] },
    chartEmpty: false,
    records: [],
    loading: true,
    loadFailed: false
  },

  onShow() { this.loadAll(); },

  loadAll() {
    var _this = this;
    var u = app.globalData.userInfo || {};
    var memberId = u.memberId || u.id || 0;
    this.setData({ memberId: memberId, loading: true, loadFailed: false });
    this.loadRecords(memberId);
    this.loadTrend(memberId);
    this.loadMember(memberId);
  },

  loadRecords(memberId) {
    var _this = this;
    request.get('/api/fitness-tests/member/' + memberId, {}, function(r) {
      var records = (r && r.records) || [];
      _this.setData({ records: records, loading: false });
      _this.computeLatest(records);
    }, function() {
      _this.setData({ loadFailed: true, loading: false });
    });
  },

  loadTrend(memberId) {
    var _this = this;
    request.get('/api/fitness-tests/trend', { memberId: memberId }, function(r) {
      if (r && r.dates) {
        _this.setData({
          trend: {
            dates: r.dates || [],
            weights: r.weights || [],
            bodyFats: r.bodyFats || [],
            muscles: r.muscles || []
          }
        });
        _this.drawChart();
      } else {
        _this.setData({ chartEmpty: true });
      }
    }, function() {
      _this.setData({ chartEmpty: true });
    });
  },

  loadMember(memberId) {
    var _this = this;
    request.get('/api/members/' + memberId, {}, function(m) {
      if (m && m.height) _this.setData({ height: m.height });
      _this.computeLatest(_this.data.records);
    });
  },

  computeLatest(records) {
    if (!records || records.length === 0) return;
    var cur = records[0];
    var prev = records[1];
    var latest = {
      weight: cur.weightKg != null ? Number(cur.weightKg).toFixed(1) : '-',
      bodyFat: cur.bodyFatPercent != null ? Number(cur.bodyFatPercent).toFixed(1) : '-',
      muscle: cur.muscleMassKg != null ? Number(cur.muscleMassKg).toFixed(1) : '-',
      bmi: '-'
    };
    var h = Number(this.data.height) || 0;
    if (h > 0 && cur.weightKg != null) {
      var hm = h / 100;
      latest.bmi = (Number(cur.weightKg) / (hm * hm)).toFixed(1);
    }
    this.setData({ latest: latest, deltas: this.computeDeltas(cur, prev) });
  },

  computeDeltas(cur, prev) {
    var result = { weight: { text: '', cls: '' }, bodyFat: { text: '', cls: '' }, muscle: { text: '', cls: '' } };
    if (!prev) return result;
    var weightDelta = (cur.weightKg != null && prev.weightKg != null) ? Number(cur.weightKg) - Number(prev.weightKg) : null;
    var fatDelta = (cur.bodyFatPercent != null && prev.bodyFatPercent != null) ? Number(cur.bodyFatPercent) - Number(prev.bodyFatPercent) : null;
    var muscleDelta = (cur.muscleMassKg != null && prev.muscleMassKg != null) ? Number(cur.muscleMassKg) - Number(prev.muscleMassKg) : null;
    if (weightDelta != null) result.weight = { text: this.deltaText(weightDelta), cls: weightDelta <= 0 ? 'good' : 'bad' };
    if (fatDelta != null) result.bodyFat = { text: this.deltaText(fatDelta), cls: fatDelta <= 0 ? 'good' : 'bad' };
    if (muscleDelta != null) result.muscle = { text: this.deltaText(muscleDelta), cls: muscleDelta >= 0 ? 'good' : 'bad' };
    return result;
  },

  deltaText(d) {
    var abs = Math.abs(d).toFixed(1);
    return (d > 0 ? '↑' : d < 0 ? '↓' : '→') + ' ' + abs;
  },

  retryLoad() { this.loadAll(); },

  drawChart() {
    var _this = this;
    var t = this.data.trend;
    if (!t || !t.dates || t.dates.length < 2) { this.setData({ chartEmpty: true }); return; }
    this.setData({ chartEmpty: false });
    wx.createSelectorQuery().in(this).select('#trendChart').fields({ node: true, size: true }).exec(function(res) {
      if (!res || !res[0] || !res[0].node) return;
      var canvas = res[0].node;
      var ctx = canvas.getContext('2d');
      var dpr = wx.getSystemInfoSync().pixelRatio || 2;
      var w = res[0].width, h = res[0].height;
      canvas.width = w * dpr; canvas.height = h * dpr;
      ctx.scale(dpr, dpr);
      ctx.clearRect(0, 0, w, h);

      var padL = 36, padR = 14, padT = 16, padB = 30;
      var plotW = w - padL - padR, plotH = h - padT - padB;
      var series = [
        { key: 'weights', color: '#4A6CF7' },
        { key: 'bodyFats', color: '#FF9800' },
        { key: 'muscles', color: '#00C853' }
      ];
      var allVals = [];
      series.forEach(function(s) {
        (t[s.key] || []).forEach(function(v) { if (v != null) allVals.push(Number(v)); });
      });
      if (allVals.length === 0) { _this.setData({ chartEmpty: true }); return; }
      var min = Math.min.apply(null, allVals), max = Math.max.apply(null, allVals);
      var span = (max - min) || 1;
      min = min - span * 0.15; max = max + span * 0.15;
      span = max - min;
      var n = t.dates.length;
      function x(i) { return n <= 1 ? padL + plotW / 2 : padL + (plotW * i) / (n - 1); }
      function y(v) { return padT + plotH - ((Number(v) - min) / span) * plotH; }

      ctx.strokeStyle = '#EEF0F4'; ctx.lineWidth = 1;
      ctx.fillStyle = '#9AA0B4'; ctx.font = '10px sans-serif';
      ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
      for (var g = 0; g <= 4; g++) {
        var gy = padT + (plotH * g) / 4;
        ctx.beginPath(); ctx.moveTo(padL, gy); ctx.lineTo(w - padR, gy); ctx.stroke();
        ctx.fillText((max - (span * g) / 4).toFixed(1), padL - 6, gy);
      }
      ctx.textAlign = 'center'; ctx.textBaseline = 'top';
      var step = Math.ceil(n / 6);
      for (var i = 0; i < n; i++) {
        if (i % step === 0 || i === n - 1) ctx.fillText(t.dates[i], x(i), padT + plotH + 8);
      }
      series.forEach(function(s) {
        var arr = t[s.key] || [];
        ctx.strokeStyle = s.color; ctx.lineWidth = 2; ctx.lineJoin = 'round';
        ctx.beginPath();
        var started = false;
        for (var i = 0; i < arr.length; i++) {
          if (arr[i] == null) { started = false; continue; }
          if (!started) { ctx.moveTo(x(i), y(arr[i])); started = true; } else { ctx.lineTo(x(i), y(arr[i])); }
        }
        ctx.stroke();
        ctx.fillStyle = '#FFF'; ctx.lineWidth = 2;
        for (var i = 0; i < arr.length; i++) {
          if (arr[i] == null) continue;
          ctx.strokeStyle = s.color;
          ctx.beginPath(); ctx.arc(x(i), y(arr[i]), 3, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
        }
      });
    });
  },

  goDetail(e) { wx.navigateTo({ url: '/pages/assessment-detail/index?id=' + e.currentTarget.dataset.id }); },
  goBack() { wx.navigateBack(); }
});
