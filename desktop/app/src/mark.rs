// SPDX-License-Identifier: GPL-3.0-or-later

const TILE: [f32; 3] = rgb(0x17, 0x3B, 0x57);
const RIM: [f32; 3] = rgb(0x3B, 0x82, 0xF6);
const PAGE: [f32; 3] = rgb(0xFF, 0xFF, 0xFF);
const SEND: [f32; 3] = rgb(0x3B, 0x82, 0xF6);
const RECEIVE: [f32; 3] = rgb(0xF5, 0x9E, 0x0B);
const PAGE_SHAPE: [(f32, f32); 5] = [(31.0, 18.0), (56.0, 18.0), (70.0, 32.0), (70.0, 81.0), (31.0, 81.0)];
const FOLD: [(f32, f32); 3] = [(56.0, 18.0), (56.0, 32.0), (70.0, 32.0)];
const SEND_SHAFT: [(f32, f32); 2] = [(38.0, 45.0), (62.0, 45.0)];
const SEND_UP: [(f32, f32); 2] = [(62.0, 45.0), (56.0, 39.0)];
const SEND_DOWN: [(f32, f32); 2] = [(62.0, 45.0), (56.0, 51.0)];
const RECEIVE_SHAFT: [(f32, f32); 2] = [(62.0, 63.0), (38.0, 63.0)];
const RECEIVE_UP: [(f32, f32); 2] = [(38.0, 63.0), (44.0, 57.0)];
const RECEIVE_DOWN: [(f32, f32); 2] = [(38.0, 63.0), (44.0, 69.0)];
const SS: usize = 4;

const fn rgb(r: u8, g: u8, b: u8) -> [f32; 3] {
    [r as f32, g as f32, b as f32]
}

pub fn render(size: usize) -> Vec<u8> {
    let unit = 100.0 / size as f32;
    let mut out = vec![0u8; size * size * 4];
    for py in 0..size {
        for px in 0..size {
            let mut color = [0f32; 3];
            let mut covered = 0usize;
            for sy in 0..SS {
                for sx in 0..SS {
                    let p = (
                        (px as f32 + (sx as f32 + 0.5) / SS as f32) * unit,
                        (py as f32 + (sy as f32 + 0.5) / SS as f32) * unit,
                    );
                    let edge = rounded_square_distance(p.0, p.1, 3.0, 22.0);
                    if edge > 0.0 {
                        continue;
                    }
                    covered += 1;
                    let mut paint = if -edge <= (1.5f32).max(unit * 0.6) { RIM } else { TILE };
                    if inside_polygon(p, &PAGE_SHAPE) {
                        paint = PAGE;
                    }
                    if inside_polygon(p, &FOLD) {
                        paint = TILE;
                    }
                    if line_distance(p, &SEND_SHAFT)
                        .min(line_distance(p, &SEND_UP))
                        .min(line_distance(p, &SEND_DOWN))
                        <= 2.8
                    {
                        paint = SEND;
                    }
                    if line_distance(p, &RECEIVE_SHAFT)
                        .min(line_distance(p, &RECEIVE_UP))
                        .min(line_distance(p, &RECEIVE_DOWN))
                        <= 2.8
                    {
                        paint = RECEIVE;
                    }
                    for channel in 0..3 {
                        color[channel] += paint[channel];
                    }
                }
            }
            if covered == 0 {
                continue;
            }
            let i = (py * size + px) * 4;
            for channel in 0..3 {
                out[i + channel] = (color[channel] / covered as f32).round() as u8;
            }
            out[i + 3] = ((covered as f32 / (SS * SS) as f32) * 255.0).round() as u8;
        }
    }
    out
}

fn rounded_square_distance(x: f32, y: f32, inset: f32, radius: f32) -> f32 {
    let (lo, hi) = (inset + radius, 100.0 - inset - radius);
    let qx = (x.clamp(lo, hi) - x).abs();
    let qy = (y.clamp(lo, hi) - y).abs();
    if x > lo && x < hi || y > lo && y < hi {
        let to_x = (x - inset).min(100.0 - inset - x);
        let to_y = (y - inset).min(100.0 - inset - y);
        return -to_x.min(to_y);
    }
    (qx * qx + qy * qy).sqrt() - radius
}

fn inside_polygon(p: (f32, f32), points: &[(f32, f32)]) -> bool {
    let mut inside = false;
    let mut previous = points.len() - 1;
    for current in 0..points.len() {
        let (x1, y1) = points[current];
        let (x2, y2) = points[previous];
        if (y1 > p.1) != (y2 > p.1) && p.0 < (x2 - x1) * (p.1 - y1) / (y2 - y1) + x1 {
            inside = !inside;
        }
        previous = current;
    }
    inside
}

fn line_distance(p: (f32, f32), line: &[(f32, f32); 2]) -> f32 {
    let (a, b) = (line[0], line[1]);
    let (dx, dy) = (b.0 - a.0, b.1 - a.1);
    let length_squared = dx * dx + dy * dy;
    let t = if length_squared == 0.0 {
        0.0
    } else {
        (((p.0 - a.0) * dx + (p.1 - a.1) * dy) / length_squared).clamp(0.0, 1.0)
    };
    ((p.0 - (a.0 + t * dx)).powi(2) + (p.1 - (a.1 + t * dy)).powi(2)).sqrt()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pixel(img: &[u8], size: usize, x: usize, y: usize) -> [u8; 4] {
        let i = (y * size + x) * 4;
        [img[i], img[i + 1], img[i + 2], img[i + 3]]
    }

    #[test]
    fn icon_has_a_file_and_two_transfer_arrows() {
        let size = 100;
        let img = render(size);
        assert_eq!(pixel(&img, size, 0, 0)[3], 0);
        assert_eq!(pixel(&img, size, 50, 35), [255, 255, 255, 255]);
        let send = pixel(&img, size, 43, 45);
        assert!(send[2] > send[0]);
        let receive = pixel(&img, size, 57, 63);
        assert!(receive[0] > receive[2]);
    }

    #[test]
    fn renders_every_tray_size() {
        for size in [16, 20, 24, 32, 48, 64, 256] {
            let img = render(size);
            assert!(img.chunks(4).any(|p| p[3] == 255), "{}px has an opaque tile", size);
        }
    }
}
