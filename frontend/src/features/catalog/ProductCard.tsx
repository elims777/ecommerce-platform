import { useState, useEffect, useRef, useCallback } from 'react';
import { ShoppingOutlined } from '@ant-design/icons';
import type { Product, ProductImage } from '@/types/product';
import { useDisplayPrice, formatPriceOrPlaceholder, isPriceAvailable } from '@/utils/priceUtils';
import { useFavouritesStore } from '@/store/favouritesStore';
import { ClickableCard } from '@/components/navigation';

const sortImages = (images: ProductImage[]): ProductImage[] =>
    [...images].sort((a, b) => {
        if (a.isPrimary && !b.isPrimary) return -1;
        if (!a.isPrimary && b.isPrimary) return 1;
        return a.displayOrder - b.displayOrder;
    });

const HeartIcon = ({ filled }: { filled?: boolean }) => (
    <svg viewBox="0 0 24 24" width="16" height="16" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>
    </svg>
);

const CartIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 4h2.5l2 13.5h11l2-9h-14"/><circle cx="9" cy="20.5" r="1.4"/><circle cx="18" cy="20.5" r="1.4"/>
    </svg>
);

const NameWithPopover = ({ name }: { name: string }) => {
    const spanRef = useRef<HTMLSpanElement>(null);
    const [isClamped, setIsClamped] = useState(false);
    const [showPopover, setShowPopover] = useState(false);
    const [pos, setPos] = useState({ top: 0, left: 0, width: 0 });
    const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        const el = spanRef.current;
        if (el) setIsClamped(el.scrollHeight > el.clientHeight);
    }, [name]);

    const handleMouseEnter = useCallback(() => {
        if (!isClamped) return;
        if (hideTimer.current) clearTimeout(hideTimer.current);
        const el = spanRef.current;
        if (!el) return;
        const rect = el.getBoundingClientRect();
        setPos({ top: rect.top, left: rect.left, width: rect.width });
        setShowPopover(true);
    }, [isClamped]);

    const handleMouseLeave = useCallback(() => {
        hideTimer.current = setTimeout(() => setShowPopover(false), 120);
    }, []);

    return (
        <>
            <span
                ref={spanRef}
                title={isClamped ? name : undefined}
                onMouseEnter={handleMouseEnter}
                onMouseLeave={handleMouseLeave}
                style={{
                    fontSize: 'var(--text-base)', fontWeight: 500, lineHeight: 1.4, color: 'var(--ink-1)',
                    display: '-webkit-box', WebkitLineClamp: 4, WebkitBoxOrient: 'vertical',
                    overflow: 'hidden', minHeight: 76, fontFamily: 'var(--font-body)',
                    textAlign: 'justify', hyphens: 'auto',
                    cursor: isClamped ? 'help' : 'inherit',
                }}
            >
                {name}
            </span>

            {showPopover && (
                <div
                    onMouseEnter={() => { if (hideTimer.current) clearTimeout(hideTimer.current); }}
                    onMouseLeave={() => { hideTimer.current = setTimeout(() => setShowPopover(false), 120); }}
                    style={{
                        position: 'fixed',
                        top: pos.top - 8,
                        left: pos.left,
                        width: pos.width,
                        zIndex: 'var(--z-modal)' as unknown as number,
                        background: 'var(--surface)',
                        border: '1px solid var(--line-2)',
                        borderRadius: 'var(--r-3)',
                        boxShadow: 'var(--shadow-3)',
                        padding: '10px 12px',
                        fontSize: 13, lineHeight: 1.45, color: 'var(--ink-1)',
                        fontFamily: 'var(--font-body)',
                        transform: 'translateY(-100%)',
                        pointerEvents: 'auto',
                    }}
                >
                    {name}
                </div>
            )}
        </>
    );
};

interface ProductCardProps {
    product: Product;
    onAddToCart: (productId: number) => void;
}

const ProductCard = ({ product, onAddToCart }: ProductCardProps) => {
    const displayPrice = useDisplayPrice(product);
    const sortedImages = sortImages(product.images || []);
    const hasMultipleImages = sortedImages.length > 1;

    const [currentImageIndex, setCurrentImageIndex] = useState(0);
    const [isHovered, setIsHovered] = useState(false);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    const isFavourite = useFavouritesStore((s) => s.isFavourite(product.id));
    const toggleFavourite = useFavouritesStore((s) => s.toggle);

    useEffect(() => {
        if (isHovered && hasMultipleImages) {
            intervalRef.current = setInterval(() => {
                setCurrentImageIndex((prev) => (prev + 1) % sortedImages.length);
            }, 1200);
        } else {
            if (intervalRef.current) clearInterval(intervalRef.current);
            setCurrentImageIndex(0);
        }
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [isHovered, hasMultipleImages, sortedImages.length]);

    const currentImage = sortedImages[currentImageIndex];

    return (
        <ClickableCard
            to={`/products/${product.id}`}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            style={{
                width: 'var(--product-card-w)',
                background: 'var(--surface)',
                border: '1px solid var(--line-1)',
                borderRadius: 'var(--r-4)',
                overflow: 'hidden',
                display: 'flex',
                flexDirection: 'column',
                position: 'relative',
                transition: 'box-shadow 0.15s, transform 0.15s',
                boxShadow: isHovered ? 'var(--shadow-2)' : 'var(--shadow-1)',
                transform: isHovered ? 'translateY(-2px)' : 'translateY(0)',
            }}
        >
            {/* Бейджи */}
            <div style={{ position: 'absolute', top: 10, left: 10, display: 'flex', flexDirection: 'column', gap: 4, zIndex: 2 }}>
                {product.isFeatured && (
                    <span style={{
                        display: 'inline-flex', alignItems: 'center', height: 20, padding: '0 8px',
                        borderRadius: 'var(--r-full)', fontSize: 'var(--text-xs)', fontWeight: 600,
                        background: 'var(--brand-red)', color: '#fff',
                    }}>Хит</span>
                )}
            </div>

            {/* Кнопка избранного */}
            <button
                type="button"
                onClick={(e) => { e.preventDefault(); e.stopPropagation(); toggleFavourite(product.id); }}
                style={{
                    position: 'absolute', top: 10, right: 10, zIndex: 2,
                    width: 30, height: 30, borderRadius: 'var(--r-full)', border: 0,
                    background: 'var(--surface)', cursor: 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: isFavourite ? 'var(--brand-red)' : 'var(--ink-3)',
                    boxShadow: '0 1px 3px rgba(0,0,0,.08)',
                    transition: 'color 0.12s',
                }}
            >
                <HeartIcon filled={isFavourite} />
            </button>

            {/* Изображение */}
            <div
                style={{
                    aspectRatio: '1 / 0.78', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    background: 'var(--surface-2)', overflow: 'hidden', position: 'relative',
                }}
            >
                {currentImage ? (
                    <img
                        key={currentImage.id}
                        alt={currentImage.altText || product.name}
                        src={currentImage.fileUrl}
                        style={{ maxHeight: '100%', maxWidth: '100%', objectFit: 'contain', transition: 'opacity 0.3s ease-in-out' }}
                    />
                ) : (
                    <ShoppingOutlined style={{ fontSize: 48, color: 'var(--ink-4)' }} />
                )}

                {hasMultipleImages && (
                    <div style={{ position: 'absolute', bottom: 8, left: '50%', transform: 'translateX(-50%)', display: 'flex', gap: 4 }}>
                        {sortedImages.map((_, idx) => (
                            <div key={idx} style={{
                                width: idx === currentImageIndex ? 16 : 6, height: 6, borderRadius: 3,
                                background: idx === currentImageIndex ? 'var(--brand-red)' : 'rgba(0,0,0,0.2)',
                                transition: 'width 0.2s ease, background 0.2s ease',
                            }} />
                        ))}
                    </div>
                )}
            </div>

            {/* Инфо */}
            <div style={{ padding: '12px 14px 14px', flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <NameWithPopover name={product.name} />

                {/* Цена */}
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 8, marginTop: 'auto' }}>
                    <span style={{
                        fontFamily: 'var(--font-head)',
                        fontWeight: isPriceAvailable(displayPrice) ? 600 : 500,
                        fontSize: isPriceAvailable(displayPrice) ? 'var(--text-2xl)' : 'var(--text-base)',
                        color: isPriceAvailable(displayPrice) ? 'var(--ink-1)' : 'var(--ink-3)',
                        letterSpacing: '-0.02em', fontVariantNumeric: 'tabular-nums',
                    }}>
                        {formatPriceOrPlaceholder(displayPrice)}
                    </span>
                    {product.sku && (
                        <span style={{ fontSize: 'var(--text-xs)', color: 'var(--ink-3)', fontFamily: 'var(--font-mono)', whiteSpace: 'nowrap' }}>
                            {product.sku}
                        </span>
                    )}
                </div>

                {/* Кнопка в корзину */}
                <div style={{ display: 'flex', gap: 6 }}>
                    <button
                        type="button"
                        onClick={(e) => { e.preventDefault(); e.stopPropagation(); onAddToCart(product.id); }}
                        style={{
                            flex: 1, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                            height: 34, padding: '0 12px',
                            background: 'var(--brand-red)', color: '#fff',
                            border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-base)', fontWeight: 500,
                            cursor: 'pointer', fontFamily: 'var(--font-body)', transition: 'background 0.12s',
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--brand-red-hover)')}
                        onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--brand-red)')}
                    >
                        <CartIcon /> В корзину
                    </button>
                </div>
            </div>
        </ClickableCard>
    );
};

export default ProductCard;
