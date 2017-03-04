import subprocess

source_colors = ["#d23b22", "#b83621"]

themes = {
    "orange": None,
    "green": ["#1db992", "#137e64"],
    "olive": ["#b0ad05", "#797703"],
    "pink": ["#ff0082", "#b7005d"],
    "blue": ["#008fff", "#0067b6"],
    "black": ["#333333", "#1a1a1a"],
}

for name, target_colors in themes.items():
    with open("ic_app.svg") as fp:
        svg = fp.read()

    if target_colors:
        target_light, target_dark = target_colors
        source_light, source_dark = source_colors
        result = svg \
            .replace(source_light + ";fill-opacity:1", target_light + ";fill-opacity:1") \
            .replace(source_dark + ";fill-opacity:1", target_dark + ";fill-opacity:0.5")
    else:
        result = svg

    with open("ic_app_%s.svg" % name, "w") as fp:
        fp.write(result)

    subprocess.check_call(["./export.sh", "ic_app_%s.svg" % name])
