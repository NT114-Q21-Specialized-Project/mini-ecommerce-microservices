const normalizeText = (value) =>
  (value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');

const MEDIA_VERSION = '20260415-2';

export const mediaCatalog = [
  { file: 'white-cap.png', keywords: ['hat', 'cap', 'horee'], label: '10%' },
  {
    file: 'sora-denim-shirt.png',
    keywords: ['sora denim shirt', 'sora denim', 'sora', 'short sleeve denim'],
    label: '35%',
  },
  { file: 'midnight-polo.png', keywords: ['midnight', 'polo'], label: '20%' },
  { file: 'cloud-hoodie.png', keywords: ['cloud', 'hoodie'], label: '25%' },
  { file: 'navy-tailored-pants.png', keywords: ['navy tailored', 'tailored pants', 'trousers'], label: '30%' },
  { file: 'charcoal-joggers.png', keywords: ['charcoal', 'jogger', 'joggers'], label: '15%' },
  { file: 'weekend-denim-shorts.png', keywords: ['weekend denim shorts', 'weekend', 'denim shorts'], label: '20%' },
  { file: 'denim-shirt.png', keywords: ['emporia armoni', 'emporia', 'armoni', 'button'], label: '50%' },
  { file: 'grey-tee.png', keywords: ['v-neck', 'v neck', 'tshirt', 't-shirt', 'tee'], label: '40%' },
  { file: 'white-sneaker.png', keywords: ['shoe', 'sneaker', 'adisia', 'speed'], label: '50%' },
];

export const dashboardShowcaseProducts = [
  { id: 'showcase-white-cap', name: 'Hat-hat Horee', price: 225, stock: 12, isShowcase: true },
  { id: 'showcase-denim-shirt', name: 'Emporia Armoni', price: 210, stock: 8, isShowcase: true },
  { id: 'showcase-grey-tee', name: 'New V-neck Tshirt', price: 180, stock: 16, isShowcase: true },
  { id: 'showcase-white-sneaker', name: 'Adisia Speed', price: 240, stock: 5, isShowcase: true },
  { id: 'showcase-sora-denim-shirt', name: 'Sora Denim Shirt', price: 265, stock: 10, isShowcase: true },
  { id: 'showcase-midnight-polo', name: 'Midnight Polo', price: 175, stock: 14, isShowcase: true },
  { id: 'showcase-cloud-hoodie', name: 'Cloud Hoodie', price: 320, stock: 9, isShowcase: true },
  { id: 'showcase-navy-tailored-pants', name: 'Navy Tailored Pants', price: 295, stock: 11, isShowcase: true },
  { id: 'showcase-charcoal-joggers', name: 'Charcoal Joggers', price: 190, stock: 13, isShowcase: true },
  { id: 'showcase-weekend-denim-shorts', name: 'Weekend Denim Shorts', price: 205, stock: 12, isShowcase: true },
];

const toMediaSlug = (value) =>
  normalizeText(value || 'product')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'product';

export const matchMappedMedia = (product) => {
  const normalizedName = normalizeText(product?.name);
  return mediaCatalog.find((entry) =>
    entry.keywords.some((keyword) => normalizedName.includes(keyword))
  );
};

export const resolveProductMedia = (product) => {
  const matchedMedia = matchMappedMedia(product);

  if (matchedMedia) {
    return {
      src: `/dashboard-media/products/${matchedMedia.file}?v=${MEDIA_VERSION}`,
      fileLabel: matchedMedia.file,
      badge: matchedMedia.label,
    };
  }

  if (product?.imageUrl) {
    return { src: product.imageUrl, fileLabel: product.imageUrl, badge: 'New' };
  }

  const slug = toMediaSlug(product?.name);
  return {
    src: `/dashboard-media/products/${slug}.png?v=${MEDIA_VERSION}`,
    fileLabel: `${slug}.png`,
    badge: 'New',
  };
};

export const sortProductsByNewest = (items) =>
  [...items].sort((left, right) => {
    const rightTime = new Date(right?.createdAt || 0).getTime();
    const leftTime = new Date(left?.createdAt || 0).getTime();
    return rightTime - leftTime;
  });

export const pickFeaturedProducts = (items, limit = 4) => {
  const sorted = sortProductsByNewest(items);
  const mapped = sorted.filter((item) => matchMappedMedia(item));
  if (mapped.length >= limit) {
    return mapped.slice(0, limit);
  }
  return dashboardShowcaseProducts.slice(0, limit);
};
