# Updating font data

The font data is stored at `src/main/resources/assets/opencomputers/font.hex`. It is derived from two fonts:

- [funscii](https://github.com/asiekierka/funscii/tree/opencomputers), a modified fork of viznut's unscii font,
- [GNU Unifont](https://unifoundry.com/unifont/index.html), which receives regular updates to improve Unicode coverage.

To update Unifont data and build a complete `font.hex`, clone [the funscii repo](https://github.com/asiekierka/funscii/tree/opencomputers)
and follow the included instructions.
