const fs = require('fs');
const file = 'app/src/main/java/com/example/ui/Screens.kt';

if (!fs.existsSync(file)) {
    console.error('Error: Screens.kt not found at ' + file);
    process.exit(1);
}

let content = fs.readFileSync(file, 'utf8');

// Step 0: Normalize to LF
content = content.replace(/\r\n/g, '\n');

// Step 1: Remove Old nested Headset playCount block in filteredIndependentTracks via Regex!
const regexOldHeadset = /Spacer\s*\(\s*modifier\s*=\s*Modifier\.width\s*\(\s*12\.dp\s*\)\s*\)\s*Icon\s*\(\s*imageVector\s*=\s*Icons\.Default\.Headset[\s\S]*?color\s*=\s*MaterialTheme\.colorScheme\.onSurfaceVariant\.copy\s*\(\s*alpha\s*=\s*0\.65f\s*\)\s*\)/;
if (regexOldHeadset.test(content)) {
    content = content.replace(regexOldHeadset, '');
    console.log('Successfully found and removed old headset playCount block from independent tracks.');
} else {
    console.log('Warning: Regexp for old headset playCount block not found.');
}

// Step 2: Add modern Headphones icon and count row at end of filteredIndependentTracks Card Column via Regex!
const regexColumnEnd = /\}\s*\}\s*\}\s*\n\s*\/\/ Display checkmark or play states/;
const replacementColumnEnd = `                                             }
                                         }
                                     }

                                     if (!track.isMissing && track.playCount > 0) {
                                         Row(
                                             verticalAlignment = Alignment.CenterVertically,
                                             modifier = Modifier.padding(end = 4.dp)
                                         ) {
                                             Icon(
                                                 imageVector = Icons.Filled.Headphones,
                                                 contentDescription = "Plays count",
                                                 tint = MaterialTheme.colorScheme.primary,
                                                 modifier = Modifier.size(14.dp)
                                             )
                                             Spacer(modifier = Modifier.width(4.dp))
                                             Text(
                                                 text = "\${track.playCount}",
                                                 fontSize = 12.sp,
                                                 fontWeight = FontWeight.Bold,
                                                 color = MaterialTheme.colorScheme.primary
                                             )
                                         }
                                     }

                                     // Display checkmark or play states`;

if (regexColumnEnd.test(content)) {
    content = content.replace(regexColumnEnd, replacementColumnEnd);
    console.log('Successfully added modern Headphones play count row to independent tracks.');
} else {
    console.log('Warning: Regexp for independent tracks card end not found.');
}

// Step 3: Add Headphones playCount row in PlaylistDetailsScreen
const targetPlaylistCardColumn = `                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isCurrentExecuting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatDuration(track.duration),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }`;

const replacementPlaylistCardColumn = `                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isCurrentExecuting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatDuration(track.duration),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }

                            if (track.playCount > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Headphones,
                                        contentDescription = "Plays count",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "\${track.playCount}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }`;

if (content.includes(targetPlaylistCardColumn)) {
    content = content.replace(targetPlaylistCardColumn, replacementPlaylistCardColumn);
    console.log('Successfully added Headphones to PlaylistDetailsScreen.');
} else {
    console.log('PlaylistDetailsScreen already modified.');
}

// Step 4: Add Headphones playCount row in SelectTracksDialog via Regex!
const regexSelectTracksDialogColumn = /Column\(\s*modifier\s*=\s*Modifier\.weight\s*\(\s*1f\s*\)\s*\)\s*\{\s*Text\(\s*text\s*=\s*track\.fileName[\s\S]*?color\s*=\s*Color\.Gray\s*\)\s*\}/;
const replacementSelectTracksDialogColumn = `Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.fileName,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val parentFolderName = if (track.parentFolderId != null) "📁 " + Loc.getText("library") else "🎵 " + Loc.getText("independent_track")
                                            Text(
                                                text = "$parentFolderName | \${formatDuration(track.duration)}",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        if (track.playCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(end = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Headphones,
                                                    contentDescription = "Plays count",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "\${track.playCount}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }`;

if (regexSelectTracksDialogColumn.test(content)) {
    content = content.replace(regexSelectTracksDialogColumn, replacementSelectTracksDialogColumn);
    console.log('Successfully added modern Headphones play count row to SelectTracksDialog.');
} else {
    console.log('Warning: Regexp for SelectTracksDialog column not found.');
}

fs.writeFileSync(file, content, 'utf8');
console.log('Screens.kt updated successfully!');
