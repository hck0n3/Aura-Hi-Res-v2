const {readWav,echoDelays}=require('./ceps.js');
const {x,sr}=readWav(process.argv[2]);const skips=[40,100,160,220];const N=8192;const rows=[];
// 0.37 s windows, hop 0.19 s: finer in time so a short desync after a change is visible
for(let s=0;s+N*2<=x.length;s+=N){const r=echoDelays(x.subarray(s,s+N*2),sr,{N,hop:N/2,minMs:20,maxMs:300,fLo:300,fHi:3000});if(!r)continue;const p=r.peaks[0];rows.push({t:(s+N)/sr,ms:p.ms,snr:p.snr});}
const art=(r)=>Math.abs(r.ms-92.88)<0.6;
const hit=r=>r.snr>=7&&!art(r);
console.log('windows',rows.length,'delayed-copy windows (>=20ms):',rows.filter(hit).length);
for(const sk of skips){const w=rows.filter(r=>r.t>=sk-3&&r.t<sk+45);const parts=[];for(let a=-3;a<45;a+=3){const b=w.filter(r=>r.t>=sk+a&&r.t<sk+a+3);const h=b.filter(hit);parts.push((a>=0?'+':'')+a+'s:'+(h.length?h.map(x=>Math.round(x.ms)).join('/'):'-'));}console.log('skip@'+sk+'s  '+parts.join('  '));}
const quiet=rows.filter(r=>!skips.some(sk=>r.t>=sk-3&&r.t<sk+45));console.log('outside skip zones: windows',quiet.length,'with delayed copy',quiet.filter(hit).length,quiet.filter(hit).map(r=>Math.round(r.ms)).join(' '));
