import { useState, useEffect, useRef } from 'react';
import { Badge } from 'antd';
import { ShoppingCartOutlined, ShoppingOutlined } from '@ant-design/icons';
import type { Product, ProductImage } from '@/types/product';

const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

const sortImages = (images: ProductImage[]): ProductImage[] =>
    [...images].sort((a, b) => {
        if (a.isPrimary && !b.isPrimary) return -1;
        if (!a.isPrimary && b.isPrimary) return 1;
        return a.displayOrder - b.displayOrder;
    });

interface ProductCardProps {
    product: Product;
    onClick: () => void;
    onAddToCart: (productId: number) => void;
}

const ProductCard = ({ product, onClick, onAddToCart }: ProductCardProps) => {
    const sortedImages = sortImages(product.images || []);
    const hasMultipleImages = sortedImages.length > 1;

    const [currentImageIndex, setCurrentImageIndex] = useState(0);
    const [isHovered, setIsHovered] = useState(false);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

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

    const cardContent = (
        <div
            onClick={onClick}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            style={{
                background: 'var(--surface)',
                border: '1px solid var(--line-1)',
                borderRadius: 8,
                overflow: 'hidden',
                cursor: 'pointer',
                transition: 'box-shadow 0.15s, transform 0.15s',
                boxShadow: isHovered ? 'var(--shadow-2)' : 'var(--shadow-1)',
                transform: isHovered ? 'translateY(-2px)' : 'translateY(0)',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            <div
                style={{
                    height: 200,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: 'var(--surface-2)',
                    overflow: 'hidden',
                    position: 'relative',
                }}
            >
                {currentImage ? (
                    <img
                        key={currentImage.id}
                        alt={currentImage.altText || product.name}
                        src={currentImage.fileUrl}
                        style={{
                            maxHeight: '100%',
                            maxWidth: '100%',
                            objectFit: 'contain',
                            transition: 'opacity 0.3s ease-in-out',
                        }}
                    />
                ) : (
                    <ShoppingOutlined style={{ fontSize: 48, color: 'var(--ink-4)' }} />
                )}

                {hasMultipleImages && (
                    <div
                        style={{
                            position: 'absolute',
                            bottom: 8,
                            left: '50%',
                            transform: 'translateX(-50%)',
                            display: 'flex',
                            gap: 4,
                        }}
                    >
                        {sortedImages.map((_, idx) => (
                            <div
                                key={idx}
                                style={{
                                    width: idx === currentImageIndex ? 16 : 6,
                                    height: 6,
                                    borderRadius: 3,
                                    background: idx === currentImageIndex
                                        ? 'var(--brand-red)'
                                        : 'rgba(0,0,0,0.2)',
                                    transition: 'width 0.2s ease, background 0.2s ease',
                                }}
                            />
                        ))}
                    </div>
                )}
            </div>

            <div style={{ padding: '12px 14px', flex: 1, display: 'flex', flexDirection: 'column', gap: 10 }}>
                <span
                    title={product.name}
                    style={{
                        fontSize: 13.5,
                        fontWeight: 500,
                        lineHeight: 1.4,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        color: 'var(--ink-1)',
                        height: '38px',
                        fontFamily: 'var(--font-body)',
                    }}
                >
                    {product.name}
                </span>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginTop: 'auto' }}>
                    <span
                        style={{
                            fontFamily: 'var(--font-head)',
                            fontWeight: 600,
                            fontSize: 18,
                            color: 'var(--brand-red)',
                            letterSpacing: '-0.02em',
                            fontVariantNumeric: 'tabular-nums',
                            flexShrink: 0,
                        }}
                    >
                        {formatPrice(product.price)}
                    </span>
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            onAddToCart(product.id);
                        }}
                        style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 6,
                            height: 34,
                            padding: '0 12px',
                            background: 'var(--brand-red)',
                            color: '#fff',
                            border: 'none',
                            borderRadius: 6,
                            fontSize: 13,
                            fontWeight: 500,
                            cursor: 'pointer',
                            fontFamily: 'var(--font-body)',
                            transition: 'background 0.12s',
                            flexShrink: 0,
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--brand-red-hover)')}
                        onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--brand-red)')}
                    >
                        <ShoppingCartOutlined />
                        В корзину
                    </button>
                </div>
            </div>
        </div>
    );

    if (product.isFeatured) {
        return (
            <Badge.Ribbon text="Хит" color="var(--brand-red)">
                {cardContent}
            </Badge.Ribbon>
        );
    }

    return cardContent;
};

export default ProductCard;
