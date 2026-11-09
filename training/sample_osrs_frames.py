"""Decode sampled video frames and create contact sheets for manual labels."""
import argparse
import json
import subprocess
import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw

from collect_osrs_sources import ROOT, CACHE
SIZE = (144, 96)


def frames(filename, seconds):
    cmd = [imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error", "-i", str(CACHE/filename), "-an",
           "-vf", f"fps=1/{seconds}:start_time=0,scale={SIZE[0]}:{SIZE[1]}:force_original_aspect_ratio=decrease,"
                  f"pad={SIZE[0]}:{SIZE[1]}:(ow-iw)/2:(oh-ih)/2",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"]
    result = subprocess.run(cmd, check=True, capture_output=True, timeout=90)
    return np.frombuffer(result.stdout, dtype=np.uint8).reshape((-1,SIZE[1],SIZE[0],3))


def main():
    media = json.loads((ROOT/"training/osrs-media-manifest.json").read_text(encoding="utf-8"))
    for index, clip in enumerate(media):
        stride = 25 if "Ironman" in clip["file"] else 3
        samples = frames(clip["file"], stride)
        if not len(samples):
            samples = frames(clip["file"], 1)
            stride=1
        count=min(28,len(samples))
        sheet=Image.new("RGB",(SIZE[0]*4,(SIZE[1]+16)*((count+3)//4)),(20,20,20))
        pen=ImageDraw.Draw(sheet)
        for i in range(count):
            x,y=(i%4)*SIZE[0],(i//4)*(SIZE[1]+16)
            sheet.paste(Image.fromarray(samples[i]),(x,y))
            pen.text((x+2,y+SIZE[1]),f"~{i*stride}s",fill=(255,255,255))
        output=CACHE/f"contact-{index}.png"
        sheet.save(output)
        print(index,clip["file"],count,output)


if __name__=="__main__":
    main()
