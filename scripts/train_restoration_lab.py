# SPDX-License-Identifier: MIT
# Original synthetic-only experiment; not a production photo model.
import argparse,json,time,hashlib,platform
from pathlib import Path
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
N=64

def fixtures(seed,count):
    rng=np.random.default_rng(seed); y,x=np.mgrid[0:N,0:N]; out=[]
    for k in range(count):
        im=np.full((N,N),rng.uniform(.12,.85),dtype=np.float32)
        for _ in range(9):
            a,b=rng.integers(0,N-5,2); w,h=rng.integers(4,30,2)
            im[b:b+h,a:a+w]=rng.uniform(.06,.94)
        if k%3==0: im += .10*np.sin((x+y)*rng.uniform(.16,.7))
        if k%3==1: im += .08*((x//3+y//3)%2)
        if k%3==2: im += .10*(x/N)
        out.append(np.clip(im,0,1))
    return torch.tensor(np.stack(out))[:,None]

def degrade(y,task):
    if task=='deblur': return F.avg_pool2d(F.pad(y,(3,3,0,0),mode='replicate'),(1,7),stride=1)
    return F.interpolate(F.avg_pool2d(y,2),scale_factor=2,mode='bilinear',align_corners=False)

class Net(nn.Module):
    def __init__(self):
        super().__init__(); self.c1=nn.Conv2d(1,4,3,padding=1,padding_mode='replicate'); self.c2=nn.Conv2d(4,1,3,padding=1,padding_mode='replicate')
    def forward(self,x): return (x+self.c2(F.relu(self.c1(x)))).clamp(0,1)

def psnr(a,b):return float(-10*torch.log10(torch.mean((a-b)**2)))

def main(root):
    root.mkdir(parents=True,exist_ok=True)
    torch.set_num_threads(1);torch.manual_seed(211);torch.use_deterministic_algorithms(True)
    train=fixtures(211,96);test=fixtures(9211,24);results={}
    for task in ['deblur','sr2x']:
        net=Net();opt=torch.optim.Adam(net.parameters(),lr=.008);x=degrade(train,task);z=degrade(test,task)
        for i in range(240):
            ix=torch.arange(i*8,i*8+8)%len(train);opt.zero_grad();loss=F.mse_loss(net(x[ix]),train[ix]);loss.backward();opt.step()
        with torch.no_grad():
            weights=torch.cat([p.flatten() for p in net.parameters()]).tolist();data='\n'.join(format(v,'.9g') for v in weights)+'\n';(root/(task+'.weights.txt')).write_text(data)
            start=time.perf_counter();pred=net(z);ms=(time.perf_counter()-start)*1000/len(test)
            rows=[(psnr(z[i],test[i]),psnr(pred[i],test[i])) for i in range(len(test))]
            results[task]={'baseline_psnr_db':psnr(z,test),'model_psnr_db':psnr(pred,test),'improved_cases':sum(b>a for a,b in rows),'cases':len(rows),'min_delta_db':min(b-a for a,b in rows),'mean_inference_ms_64x64_batch24':ms,'weights_sha256':hashlib.sha256(data.encode()).hexdigest(),'parameters':len(weights),'per_case_psnr':rows}
            (root/(task+'.input.f32')).write_bytes(z[0].numpy().astype('<f4').tobytes());(root/(task+'.output.f32')).write_bytes(pred[0].numpy().astype('<f4').tobytes())
    report={'scope':'synthetic grayscale host experiment, not Android or real-photo benchmark','torch':torch.__version__,'python':platform.python_version(),'train_seed':211,'test_seed':9211,'train_cases':96,'holdout_cases':24,'models':results}
    (root/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:{a:b for a,b in v.items() if a!='per_case_psnr'} for k,v in results.items()},indent=2))
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    main(parser.parse_args().out)
