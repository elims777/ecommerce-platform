import { useState, useEffect, useRef } from 'react';
import { Card, Typography, Badge, Button } from 'antd';
import { ShoppingCartOutlined, ShoppingOutlined } from '@ant-design/icons';
import type { Product, ProductImage } from '@/types/product';

const { Text } = Typography;

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
        <Card
            hoverable
            onClick={onClick}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            styles={{ body: { padding: '12px 16px' } }}
            cover={
                <div
                    style={{
                        height: 200,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: '#fafafa',
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
                        <ShoppingOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />
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
                                            ? '#1677ff'
                                            : 'rgba(0,0,0,0.2)',
                                        transition: 'width 0.2s ease, background 0.2s ease',
                                    }}
                                />
                            ))}
                        </div>
                    )}
                </div>
            }
        >
            {/* Название */}
            <span title={product.name}>
                <Text
                    strong
                    style={{
                        fontSize: 14,
                        lineHeight: '1.5',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        marginBottom: 10,
                        color: '#262626',
                        wordBreak: 'normal',
                        overflowWrap: 'break-word',
                        height: '42px',
                    }}
                >
                    {product.name}
                </Text>
            </span>

            {/* Цена + кнопка */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                <Text strong style={{ fontSize: 16, color: '#1677ff', flexShrink: 0 }}>
                    {formatPrice(product.price)}
                </Text>
                <Button
                    type="primary"
                    size="small"
                    icon={<ShoppingCartOutlined />}
                    onClick={(e) => {
                        e.stopPropagation(); // не открываем карточку товара
                        onAddToCart(product.id);
                    }}
                >
                    В корзину
                </Button>
            </div>
        </Card>
    );

    if (product.isFeatured) {
        return (
            <Badge.Ribbon text="Хит" color="red">
                {cardContent}
            </Badge.Ribbon>
        );
    }

    return cardContent;
};

export default ProductCard;