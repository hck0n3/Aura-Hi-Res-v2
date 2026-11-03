const {readWav}=require('./ceps.js');
const {x0,sr}=(()=>{const r=readWav(process.argv[2]);return{x0:r.x,sr:r.sr};})();
// high-pass (1-pole) to keep the click transient, 1 ms envelope, attack score = rise of the envelope over 2 ms
let y=new Float64Array(x0.length),a=0.97,px=0,py=0;for(let i=0;i<x0.length;i++){py=a*(py+x0[i]-px);px=x0[i];y[i]=py;}
const w=Math.round(sr*0.001);const env=[];for(let i=0;i+w<=y.length;i+=w){let e=0;for(let j=0;j<w;j++)e+=y[i+j]**2;env.push(Math.sqrt(e/w));}
const rise=env.map((v,i)=>i<3?0:v-env[i-3]);
const sorted=[...rise].sort((a,b)=>a-b);const hi=sorted[Math.floor(sorted.length*0.998)];const thr=hi*0.18;
// group into beeps: find strong attacks, a new beep when gap > 400 ms
const att=[];let last=-1e9;for(let i=3;i<rise.length-3;i++){if(rise[i]>thr&&rise[i]>=rise[i-1]&&rise[i]>=rise[i+1]&&i-last>12){att.push({t:i,s:rise[i]});last=i;}}
const beeps=[];for(const a of att){const b=beeps[beeps.length-1];if(b&&a.t-b[0].t<400)b.push(a);else beeps.push([a]);}
console.log('attacks',att.length,'beeps',beeps.length);
const out=beeps.map(b=>b.length<2?'1':(b.slice(1).map(a=>a.t-b[0].t).join('/')+'('+b.length+')'));
console.log(out.join('  '));
