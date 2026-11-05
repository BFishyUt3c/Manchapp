from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.tools' / 'python-libs'))
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

source = ROOT / 'public' / 'fonts' / 'nunito.ttf'
font = TTFont(source)
print('Ejes originales:', [(axis.axisTag, axis.defaultValue) for axis in font['fvar'].axes])
for name, weight in [('Regular', 400), ('Bold', 700), ('ExtraBold', 800)]:
    static = instantiateVariableFont(TTFont(source), {'wght': weight}, inplace=True)
    target = ROOT / 'public' / 'fonts' / f'Nunito-{name}.ttf'
    static.save(target)
    print(target.name, target.stat().st_size, 'bytes')
