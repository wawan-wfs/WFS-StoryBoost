# SPDX-License-Identifier: MIT
"""Offline CC0 photo benchmark. Controlled degradations, not naturally blurred photos."""
import argparse,hashlib,json,time
from pathlib import Path
import numpy as np
import torch
from torch.nn import functional as F
from PIL import Image
from skimage import data,metrics,__version__
from refine_restoration_lab import load

def run(out,old,refined):
    out.mkdir(parents=True,exist_ok=True);torch.set_num_threads(1);rows=[]
    for name,source in [('camera',data.camera()),('cat',data.cat()),('brick',data.brick())]:
        raw=Image.fromarray(source).convert('L');raw=raw.crop((0,0,raw.width//2*2,raw.height//2*2));truth=np.asarray(raw,dtype=np.float32)/255
        y=torch.from_numpy(truth.copy())[None,None];raw.save(out/(name+'-truth.png'))
        for task in ('deblur','sr2x'):
            if task=='deblur':x=F.avg_pool2d(F.pad(y,(3,3,0,0),mode='replicate'),(1,7),stride=1);bic=None
            else:
                low=F.avg_pool2d(y,2);x=F.interpolate(low,scale_factor=2,mode='bilinear',align_corners=False);bic=F.interpolate(low,scale_factor=2,mode='bicubic',align_corners=False).clamp(0,1)
            a=load(old/(task+'.weights.txt'));b=load(refined/(task+'.weights.txt'))
            with torch.no_grad():
                start=time.perf_counter();prior=a(x);old_ms=(time.perf_counter()-start)*1000
                start=time.perf_counter();new=b(x);new_ms=(time.perf_counter()-start)*1000
            row={'photo':name,'task':task,'shape':list(truth.shape),'source_sha256':hashlib.sha256(source.tobytes()).hexdigest(),'prior_ms':old_ms,'refined_ms':new_ms,'metrics':{}}
            variants={'degraded':x,'original_model':prior,'refined_model':new}
            if bic is not None:variants['bicubic']=bic
            for label,t in variants.items():
                v=t.squeeze().numpy();row['metrics'][label]={'psnr':float(metrics.peak_signal_noise_ratio(truth,v,data_range=1)),'ssim':float(metrics.structural_similarity(truth,v,data_range=1))}
                Image.fromarray(np.rint(v*255).astype('uint8')).save(out/(name+'-'+task+'-'+label+'.png'))
            rows.append(row)
    report={'scope':'CC0 grayscale photo, controlled blur/downsampling, host PyTorch only','skimage':__version__,'source_license':'https://scikit-image.org/docs/stable/api/skimage.data.html','cases':rows};(out/'photo-report.json').write_text(json.dumps(report,indent=2)+'\n')
    for r in rows:print(r['photo'],r['task'],{k:(round(v['psnr'],3),round(v['ssim'],5)) for k,v in r['metrics'].items()})
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--old',type=Path,required=True);p.add_argument('--refined',type=Path,required=True);a=p.parse_args();run(a.out,a.old,a.refined)
