# Third-party notices

## FFmpeg

The Android package contains an FFmpeg command-line executable built from
FFmpeg tag `n8.1.2`, commit
`38b88335f99e76ed89ff3c93f877fdefce736c13`.

FFmpeg is licensed under the GNU Lesser General Public License, version 2.1 or
later. The packaged build uses FFmpeg's built-in LGPL-compatible components and
Android system libraries. Its build configuration disables GPL and nonfree
components.

- Upstream source: <https://github.com/FFmpeg/FFmpeg/tree/38b88335f99e76ed89ff3c93f877fdefce736c13>
- Full local license text: [`LICENSE-FFMPEG-LGPL-2.1.txt`](LICENSE-FFMPEG-LGPL-2.1.txt)
- Upstream license file: <https://github.com/FFmpeg/FFmpeg/blob/38b88335f99e76ed89ff3c93f877fdefce736c13/COPYING.LGPLv2.1>
- Local reproducible build instructions: [`native/ffmpeg/README.md`](native/ffmpeg/README.md)

The executable also prints its license and exact build configuration when run
with `-L` and `-version`.
