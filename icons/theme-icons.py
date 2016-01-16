import subprocess

source_colors = ["ee4d2e", "b83621"]

themes = {
    "orange": ["ee4d2e", "b83621"],
    "green": ["1db992", "137e64"],
    "olive": ["b0ad05", "797703"],
    "pink": ["ff0082", "b7005d"]
}

for name, (color_light, color_dark) in themes.items():
    with open("ic_app.svg") as fp:
        svg = fp.read()

    source_light, source_dark = source_colors
    result = svg.replace(source_light, color_light).replace(source_dark, color_dark)
    with open("ic_app_%s.svg" % name, "w") as fp:
        fp.write(result)

    subprocess.check_call(["./export.sh", "ic_app_%s.svg" % name])
