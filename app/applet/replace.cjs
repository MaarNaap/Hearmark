const fs = require('fs');
const file = '../app/src/main/java/com/example/ui/Screens.kt';

if (!fs.existsSync(file)) {
    console.error('Error: Screens.kt not found at ' + file);
    process.exit(1);
}

let content = fs.readFileSync(file, 'utf8');

// Step 0: Normalize to LF
content = content.replace(/\r\n/g, '\n');

// Step 1: Remove Old Headset playCount block in filteredIndependentTracks
const oldHeadsetBlock = `                                                   Spacer(modifier = Modifier.width(12.dp))
                                                   Icon(
                                                       imageVector = Icons.Default.Headset,
                                                       contentDescription = null,
                                                       tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                       modifier = Modifier.size(12.dp)
                                                   )
                                                   Spacer(modifier = Modifier.width(4.dp))
                                                   Text(
                                                       text = "",
                                                       style = MaterialTheme.typography.bodySmall,
                                                       color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                                   )`;

if (content.includes(oldHeadsetBlock)) {
    content = content.replace(oldHeadsetBlock, '');
    console.log('Normalized and removed old headset playCount block from independent tracks.');
} else {
    console.log('Warning: Old headset playcount block not found.');
}

// Step 2: Add modern Headphones icon and count row at end of filteredIndependentTracks Card
const targetIndependentCardEnd = `                                          if (track.isMissing) {
                                              Text(
                                                  text = Loc.getText("missing_file_warning"),
                                                  color = MaterialTheme.colorScheme.error,
                                                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                              )
                                          } else {
                                              Row(verticalAlignment = Alignment.CenterVertically) {
                                                  Icon(
                                                      imageVector = Icons.Default.AccessTime,
                                                      contentDescription = null,
                                                      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                      modifier = Modifier.size(12.dp)
                                                   )
                                                   Spacer(modifier = Modifier.width(4.dp))
                                                   Text(
                                                       text = formatDuration(track.duration),
                                                       style = MaterialTheme.typography.bodySmall,
                                                       color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                                   )
                                              }
                                          }
                                      }`;

const replacementIndependentCardEnd = `                                          if (track.isMissing) {
                                              Text(
                                                  text = Loc.getText("missing_file_warning"),
                                                  color = MaterialTheme.colorScheme.error,
                                                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                              )
                                          } else {
                                              Row(verticalAlignment = Alignment.CenterVertically) {
                                                  Icon(
                                                      imageVector = Icons.Default.AccessTime,
                                                      contentDescription = null,
                                                      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                      modifier = Modifier.size(12.dp)
                                                   )
                                                   Spacer(modifier = Modifier.width(4.dp))
                                                   Text(
                                                       text = formatDuration(track.duration),
                                                       style = MaterialTheme.typography.bodySmall,
                                                       color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                                   )
                                              }
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
                                          Spacer(modifier = Modifier.width(4.dp))
                                      }`;

if (content.includes(targetIndependentCardEnd)) {
    content = content.replace(targetIndependentCardEnd, replacementIndependentCardEnd);
    console.log('Successfully added modern Headphones play count row to independent tracks.');
} else {
    // Let's print substring of the file to see why it didn't match if it fails
    console.log('Warning: Target independant card end not found.');
}

// Step 3: Replace the playCount tag in FolderDetailsScreen
const targetFolderStatus = `                            val statusText = when {
                                track.playCount > 0 -> "🎧 \${track.playCount}"
                                percent > 0 -> Loc.getText("in_progress") + " (\$percent%)"
                                else -> Loc.getText("not_started")
                            }

                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (track.playCount > 0) MaterialTheme.colorScheme.primary else Color.Gray
                            )`;

const replacementFolderStatus = `                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (track.playCount > 0) {
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
                                } else {
                                    val statusText = if (percent > 0) Loc.getText("in_progress") + " (\$percent%)" else Loc.getText("not_started")
                                    Text(
                                        text = statusText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                }
                            }`;

if (content.includes(targetFolderStatus)) {
    content = content.replace(targetFolderStatus, replacementFolderStatus);
    console.log('Successfully replaced playcount status representation in FolderDetailsScreen.');
} else {
    console.log('Warning: Target folder status not found.');
}

// Step 4: Add Headphones playCount row in PlaylistDetailsScreen
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
    console.log('Successfully added modern Headphones play count row to PlaylistDetailsScreen.');
} else {
    console.log('Warning: Target playlist card column not found.');
}

// Step 5: Add Headphones playCount row in SelectTracksDialog
const targetSelectTracksDialogColumn = `                                         Column(modifier = Modifier.weight(1f)) {
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
                                         }`;

const replacementSelectTracksDialogColumn = `                                         Column(modifier = Modifier.weight(1f)) {
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

if (content.includes(targetSelectTracksDialogColumn)) {
    content = content.replace(targetSelectTracksDialogColumn, replacementSelectTracksDialogColumn);
    console.log('Successfully added modern Headphones play count row to SelectTracksDialog.');
} else {
    console.log('Warning: Target select tracks dialog column not found.');
}

fs.writeFileSync(file, content, 'utf8');
console.log('Screens.kt updated successfully!');
