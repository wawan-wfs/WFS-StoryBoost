#!/usr/bin/env python3
"""Exploratory strength sweep on prior CC0 benchmark PNGs; not a fresh holdout."""
import argparse,json
from pathlib import Path
import numpy as np
from PIL import Image
from skimage.metrics import peak_signal_noise_ratio,structural_similarity
def main(root,out):
    rows=[]
    for photo in ('camera','cat','brick'):
        truth=np.asarray(Image.open(root/f'{photo}-truth.png'),dtype=np.float32)/255
        for task in ('deblur','sr2x'):
            a=np.asarray(Image.open(root/f'{photo}-{task}-degraded.png'),dtype=np.float32)/255
            b=np.asarray(Image.open(root/f'{photo}-{task}-refined_model.png'),dtype=np.float32)/255
            row={'photo':photo,'task':task,'metrics':[]}
            for strength in (0,25,50,75,100):
                y=a+(b-a)*strength/100
                row['metrics'].append({'strength':strength,'psnr':float(peak_signal_noise_ratio(truth,y,data_range=1)),'ssim':float(structural_similarity(truth,y,data_range=1))})
            rows.append(row)
    out.write_text(json.dumps({'scope':'host, archived 8-bit CC0 benchmark outputs, controlled degradation, exploratory not independent','cases':rows},indent=2)+'\n')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--photos',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();main(a.photos,a.output)
