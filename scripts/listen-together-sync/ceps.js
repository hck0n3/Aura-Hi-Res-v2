const fs=require('fs');
function readWav(f){const b=fs.readFileSync(f);let p=12,fmt,data;while(p<b.length-8){const id=b.toString('ascii',p,p+4),sz=b.readUInt32LE(p+4);if(id==='fmt ')fmt={ch:b.readUInt16LE(p+10),sr:b.readUInt32LE(p+12)};if(id==='data'){data=b.subarray(p+8,p+8+sz);break;}p+=8+sz+(sz&1);}const n=Math.floor(data.length/2/fmt.ch);const x=new Float64Array(n);for(let i=0;i<n;i++){let s=0;for(let c=0;c<fmt.ch;c++)s+=data.readInt16LE((i*fmt.ch+c)*2);x[i]=s/fmt.ch/32768;}return{x,sr:fmt.sr};}
function fft(re,im,inv){const n=re.length;for(let i=1,j=0;i<n;i++){let bit=n>>1;for(;j&bit;bit>>=1)j^=bit;j^=bit;if(i<j){[re[i],re[j]]=[re[j],re[i]];[im[i],im[j]]=[im[j],im[i]];}}for(let len=2;len<=n;len<<=1){const ang=2*Math.PI/len*(inv?-1:1);const wr=Math.cos(ang),wi=Math.sin(ang);for(let i=0;i<n;i+=len){let cr=1,ci=0;for(let k=0;k<len/2;k++){const ur=re[i+k],ui=im[i+k];const vr=re[i+k+len/2]*cr-im[i+k+len/2]*ci;const vi=re[i+k+len/2]*ci+im[i+k+len/2]*cr;re[i+k]=ur+vr;im[i+k]=ui+vi;re[i+k+len/2]=ur-vr;im[i+k+len/2]=ui-vi;const t=cr*wr-ci*wi;ci=cr*wi+ci*wr;cr=t;}}}if(inv)for(let i=0;i<n;i++){re[i]/=n;im[i]/=n;}}
function echoDelays(x,sr,{N=32768,hop=8192,minMs=8,maxMs=400,fLo=300,fHi=6000}={}){
  const logAvg=new Float64Array(N/2+1);let frames=0;
  for(let s=0;s+N<=x.length;s+=hop){const re=new Float64Array(N),im=new Float64Array(N);let e=0;for(let i=0;i<N;i++){const w=0.5-0.5*Math.cos(2*Math.PI*i/(N-1));re[i]=x[s+i]*w;e+=re[i]*re[i];}if(e<1e-6)continue;fft(re,im,false);for(let k=0;k<=N/2;k++)logAvg[k]+=Math.log(re[k]*re[k]+im[k]*im[k]+1e-12);frames++;}
  if(!frames)return null;for(let k=0;k<=N/2;k++)logAvg[k]/=frames;
  // band-limit, remove the smooth envelope (running mean over ~40 Hz... via subtracting moving average)
  const binHz=sr/N, lo=Math.floor(fLo/binHz), hi=Math.floor(fHi/binHz), W=Math.max(3,Math.round(150/binHz));
  const r=new Float64Array(N/2+1);for(let k=lo;k<=hi;k++){let s=0,c=0;for(let j=Math.max(lo,k-W);j<=Math.min(hi,k+W);j++){s+=logAvg[j];c++;}r[k]=logAvg[k]-s/c;}
  // cepstrum of the detrended log spectrum (even extension)
  const re=new Float64Array(N),im=new Float64Array(N);for(let k=0;k<=N/2;k++){re[k]=r[k];if(k>0&&k<N/2)re[N-k]=r[k];}fft(re,im,true);
  const out=[];for(let q=Math.floor(minMs/1000*sr);q<=Math.floor(maxMs/1000*sr);q++)out.push({ms:q/sr*1000,v:re[q]});
  const sd=Math.sqrt(out.reduce((a,o)=>a+o.v*o.v,0)/out.length);
  const peaks=out.filter((o,i)=>i>2&&i<out.length-3&&o.v>out[i-1].v&&o.v>=out[i+1].v&&o.v>out[i-2].v&&o.v>=out[i+2].v).sort((a,b)=>b.v-a.v).slice(0,5).map(o=>({ms:+o.ms.toFixed(2),snr:+(o.v/sd).toFixed(1)}));
  return{frames,peaks};}
module.exports={readWav,echoDelays};
if(require.main===module){const {x,sr}=readWav(process.argv[2]);if(process.argv[3]==='synth'){const d=Math.round(+process.argv[4]/1000*sr);const y=new Float64Array(x.length);for(let i=0;i<x.length;i++)y[i]=x[i]+0.7*(i>=d?x[i-d]:0);console.log('synthetic delay',process.argv[4],'ms ->',JSON.stringify(echoDelays(y,sr,{hop:2048})));}else console.log(JSON.stringify(echoDelays(x,sr)));}
