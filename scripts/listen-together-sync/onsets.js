const {readWav}=require('./ceps.js');
const {x,sr}=readWav(process.argv[2]);
// 1 ms RMS envelope, then onsets = rising crossings of an adaptive threshold
const w=Math.round(sr*0.001);const env=[];for(let i=0;i+w<=x.length;i+=w){let e=0;for(let j=0;j<w;j++)e+=x[i+j]**2;env.push(Math.sqrt(e/w));}
const sorted=[...env].sort((a,b)=>a-b);const med=sorted[Math.floor(sorted.length*0.5)],p99=sorted[Math.floor(sorted.length*0.995)];
const thr=Math.max(med*6,p99*0.25);console.log('envelope median',med.toFixed(4),'p99.5',p99.toFixed(3),'threshold',thr.toFixed(3));
const on=[];let armed=true,last=-1e9;for(let i=1;i<env.length;i++){if(armed&&env[i]>thr&&env[i-1]<=thr&&i-last>8){on.push(i);last=i;armed=false;}if(env[i]<thr*0.5)armed=true;}
console.log('onsets',on.length);console.log(on.slice(0,60).map(i=>i+'ms').join(' '));
const gaps=[];for(let i=1;i<on.length;i++)gaps.push(on[i]-on[i-1]);console.log('gaps(ms)',gaps.slice(0,60).join(' '));
