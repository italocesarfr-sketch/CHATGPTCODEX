"""Gera fotografias sintéticas para ensaiar OCR; nunca inclui alunos reais.
Dependência de desenvolvimento opcional: Pillow. Não faz parte do aplicativo.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parent.parent
font_path = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font = ImageFont.truetype(font_path, 38)
small = ImageFont.truetype(font_path, 28)
pages = [
    [(1, 'Estudante Fictício Alfa'), (2, 'Estudante Fictício Beta'), (3, 'Estudante Fictício Gama')],
    [(4, 'Estudante Fictício Delta'), (5, 'Estudante Fictício Épsilon'), (2, 'Estudante Fictício Beta')],
]
for page, rows in enumerate(pages, 1):
    image = Image.new('RGB', (1600, 1100), 'white')
    draw = ImageDraw.Draw(image)
    draw.text((80, 65), 'ESCOLA FICTÍCIA — LISTA DE CHAMADA', font=font, fill='black')
    draw.text((80, 140), f'Data: 20/08/2026     Turma de teste     Página {page}', font=small, fill='black')
    draw.text((80, 230), 'Número', font=small, fill='black')
    draw.text((260, 230), 'Nome completo', font=small, fill='black')
    draw.text((1250, 230), 'Frequência', font=small, fill='black')
    for i, (number, name) in enumerate(rows):
        y = 320 + 130 * i
        draw.line((70, y-25, 1520, y-25), fill='#AAAAAA', width=2)
        draw.text((90, y), str(number).zfill(2), font=font, fill='black')
        draw.text((260, y), name, font=font, fill='black')
        draw.text((1260, y), 'P  F  P', font=font, fill='black')
    draw.text((80, 940), 'DADOS SINTÉTICOS PARA TESTE — NÃO SÃO ALUNOS REAIS', font=small, fill='black')
    image.save(root / 'test-fixtures' / f'lista-ficticia-pagina-{page}.png')
(root / 'test-fixtures' / 'alunos-ficticios.csv').write_text('numero;nome\n1;Estudante Fictício Alfa\n2;Estudante Fictício Beta\n3;Estudante Fictício Gama\n', encoding='utf-8-sig')
