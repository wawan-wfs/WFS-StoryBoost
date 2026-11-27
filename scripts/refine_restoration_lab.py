# SPDX-License-Identifier: MIT
"""Fine-tune original synthetic networks and compare independent holdout and bicubic."""
import argparse,hashlib,json
from pathlib import Path
import torch
from torch.nn import functional as F
from train_restoration_lab import Net,fixtures,degrade,psnr

def load(path):
    model=Net(); v=torch.tensor([float(x) for x in path.read_text().split()]);i=0
    with torch.no_grad():
        for p in model.parameters():n=p.numel();p.copy_(v[i:i+n].reshape(p.shape));i+=n
    return model

def main(out,old):
    out.mkdir(parents=True,exist_ok=True);torch.set_num_threads(1);torch.manual_seed(222);torch.use_deterministic_algorithms(True)
    train=fixtures(222,192);test=fixtures(12222,48);report={}
    for task in ('deblur','sr2x'):
        original=load(old/(task+'.weights.txt'));model=load(old/(task+'.weights.txt'));opt=torch.optim.Adam(model.parameters(),lr=.003)
        x=degrade(train,task);z=degrade(test,task)
        for i in range(1200):
            ix=torch.arange(i*8,i*8+8)%len(train);opt.zero_grad();loss=F.mse_loss(model(x[ix]),train[ix]);loss.backward();opt.step()
        with torch.no_grad():
            weights=torch.cat([p.flatten() for p in model.parameters()]).tolist();data='\n'.join(format(v,'.9g') for v in weights)+'\n';(out/(task+'.weights.txt')).write_text(data)
            predicted=model(z); prior=original(z);rows=[(psnr(z[i],test[i]),psnr(prior[i],test[i]),psnr(predicted[i],test[i])) for i in range(len(test))]
            bicubic=F.interpolate(F.avg_pool2d(test,2),scale_factor=2,mode='bicubic',align_corners=False).clamp(0,1) if task=='sr2x' else None
            report[task]={'baseline':psnr(z,test),'prior':psnr(prior,test),'refined':psnr(predicted,test),'improved_over_prior':sum(c>b for a,b,c in rows),'improved_over_input':sum(c>a for a,b,c in rows),'worst_vs_input':min(c-a for a,b,c in rows),'bicubic':psnr(bicubic,test) if bicubic is not None else None,'sha256':hashlib.sha256(data.encode()).hexdigest(),'prior_sha256':hashlib.sha256((old/(task+'.weights.txt')).read_bytes()).hexdigest(),'per_case':rows}
            (out/(task+'.input.f32')).write_bytes(z[0].numpy().astype('<f4').tobytes());(out/(task+'.output.f32')).write_bytes(predicted[0].numpy().astype('<f4').tobytes())
    (out/'report.json').write_text(json.dumps({'scope':'synthetic independent holdout, not real photos or Android','torch':torch.__version__,'train_seed':222,'train_cases':192,'steps':1200,'holdout_seed':12222,'holdout_cases':48,'models':report},indent=2)+'\n');print(json.dumps({k:{a:b for a,b in v.items() if a!='per_case'} for k,v in report.items()},indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--old',type=Path,required=True);a=p.parse_args()
    if a.out.resolve()==a.old.resolve():raise ValueError("Output must be separate from pinned original weights")
    main(a.out,a.old)
